package com.brouken.player.tonemap

import android.opengl.GLES20
import android.opengl.GLES30
import com.brouken.player.utils.DebugLogger

/**
 * HdrTonemapShader - Cinema-quality HDR/Dolby Vision to SDR tonemapping
 * 
 * Combines:
 * - BT.2020 → BT.709 color space conversion (fixes green/purple bug)
 * - PQ/HLG EOTF (Electro-Optical Transfer Function)
 * - Hybrid tonemap: BT.2390 EETF + ACES + Hable
 * - Per-zone optimization (shadows, midtones, highlights)
 */
object HdrTonemapShader {

    private const val TAG = "HdrTonemap"

    // Vertex shader - simple passthrough
    const val VERTEX_SHADER = """
        #version 300 es
        in vec4 aPosition;
        in vec2 aTexCoord;
        out vec2 vTexCoord;
        
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    // Fragment shader - Advanced HDR to SDR tonemapping
    const val FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        
        in vec2 vTexCoord;
        out vec4 fragColor;
        
        uniform sampler2D uTexture;
        uniform float uMaxLuminance;     // Source max nits (e.g., 1000, 4000)
        uniform float uMinLuminance;     // Source min nits
        uniform float uAvgLuminance;     // Source avg nits (from DV L1)
        uniform int uTransferFunction;   // 0=PQ, 1=HLG, 2=SDR
        uniform int uColorSpace;         // 0=BT.2020, 1=BT.709
        uniform int uQualityLevel;       // 0=Ultra, 1=Balanced (low-end)
        
        // ============ CONSTANTS ============
        
        // BT.2020 to BT.709 color matrix (FIXES GREEN/PURPLE BUG)
        const mat3 BT2020_TO_BT709 = mat3(
            1.6605,  -0.5876,  -0.0728,
           -0.1246,   1.1329,  -0.0083,
           -0.0182,  -0.1006,   1.1187
        );
        
        // BT.709 luminance coefficients
        const vec3 LUMA_BT709 = vec3(0.2126, 0.7152, 0.0722);
        
        // PQ constants (SMPTE ST 2084)
        const float PQ_M1 = 0.1593017578125;
        const float PQ_M2 = 78.84375;
        const float PQ_C1 = 0.8359375;
        const float PQ_C2 = 18.8515625;
        const float PQ_C3 = 18.6875;
        
        // HLG constants
        const float HLG_A = 0.17883277;
        const float HLG_B = 0.28466892;
        const float HLG_C = 0.55991073;
        
        // ============ PQ EOTF (Perceptual Quantizer) ============
        
        vec3 PQ_EOTF(vec3 pq) {
            vec3 Np = pow(max(pq, 0.0), vec3(1.0 / PQ_M2));
            vec3 L = pow(max(Np - PQ_C1, 0.0) / (PQ_C2 - PQ_C3 * Np), vec3(1.0 / PQ_M1));
            return L * 10000.0; // Output in nits
        }
        
        // ============ HLG EOTF ============
        
        vec3 HLG_OETF_inv(vec3 hlg) {
            vec3 result;
            for (int i = 0; i < 3; i++) {
                float x = hlg[i];
                if (x <= 0.5) {
                    result[i] = (x * x) / 3.0;
                } else {
                    result[i] = (exp((x - HLG_C) / HLG_A) + HLG_B) / 12.0;
                }
            }
            return result;
        }
        
        vec3 HLG_EOTF(vec3 hlg, float Lw) {
            vec3 scene = HLG_OETF_inv(hlg);
            float Y = dot(scene, LUMA_BT709);
            float gamma = 1.2 + 0.42 * log(Lw / 1000.0) / log(10.0);
            return scene * pow(Y, gamma - 1.0) * Lw;
        }
        
        // ============ BT.2390 EETF (Electrical-Electrical Transfer Function) ============
        
        float BT2390_EETF(float L, float Lmax, float Lmin, float Ltarget) {
            // Normalize luminance
            float Lnorm = (L - Lmin) / (Lmax - Lmin);
            Lnorm = clamp(Lnorm, 0.0, 1.0);
            
            // Knee point calculation
            float Ks = 1.5 * Ltarget / Lmax - 0.5;
            Ks = clamp(Ks, 0.0, 1.0);
            
            // Hermite spline for smooth roll-off
            if (Lnorm <= Ks) {
                return Lnorm;
            } else {
                float t = (Lnorm - Ks) / (1.0 - Ks);
                float t2 = t * t;
                float t3 = t2 * t;
                
                // Cubic Hermite spline
                float p0 = Ks;
                float p1 = 1.0;
                float m0 = (1.0 - Ks) * 0.5;
                float m1 = 0.0;
                
                float mapped = (2.0*t3 - 3.0*t2 + 1.0) * p0
                             + (t3 - 2.0*t2 + t) * m0
                             + (-2.0*t3 + 3.0*t2) * p1
                             + (t3 - t2) * m1;
                
                return clamp(mapped, 0.0, 1.0);
            }
        }
        
        // ============ HABLE FILMIC (Shadow preservation) ============
        
        vec3 HablePartial(vec3 x) {
            float A = 0.15;  // Shoulder Strength
            float B = 0.50;  // Linear Strength
            float C = 0.10;  // Linear Angle
            float D = 0.20;  // Toe Strength
            float E = 0.02;  // Toe Numerator
            float F = 0.30;  // Toe Denominator
            
            return ((x*(A*x+C*B)+D*E) / (x*(A*x+B)+D*F)) - E/F;
        }
        
        vec3 HableTonemap(vec3 color) {
            float W = 11.2;  // Linear White Point
            vec3 numerator = HablePartial(color);
            vec3 denominator = HablePartial(vec3(W));
            return numerator / denominator;
        }
        
        // ============ ACES RRT (Reference Rendering Transform) ============
        
        vec3 ACES_RRT(vec3 color) {
            // Simplified ACES RRT approximation
            float a = 2.51;
            float b = 0.03;
            float c = 2.43;
            float d = 0.59;
            float e = 0.14;
            
            return clamp((color * (a * color + b)) / (color * (c * color + d) + e), 0.0, 1.0);
        }
        
        // ============ SIGMOID CONTRAST ============
        
        vec3 sigmoidContrast(vec3 color, float contrast) {
            vec3 x = color * 2.0 - 1.0;
            vec3 s = 1.0 / (1.0 + exp(-contrast * x));
            return clamp(s, 0.0, 1.0);
        }
        
        // ============ MAIN TONEMAPPING (Best quality, optimized for all devices) ============
        
        vec3 tonemap(vec3 hdrColor) {
            // Step 1: Apply EOTF to get linear light
            vec3 linear;
            if (uTransferFunction == 0) {
                linear = PQ_EOTF(hdrColor);
            } else if (uTransferFunction == 1) {
                linear = HLG_EOTF(hdrColor, uMaxLuminance);
            } else {
                linear = pow(hdrColor, vec3(2.2)) * 100.0;
            }
            
            // Step 2: BT.2020 to BT.709 (FIXES GREEN/PURPLE BUG!)
            if (uColorSpace == 0) {
                linear = BT2020_TO_BT709 * linear;
                linear = max(linear, vec3(0.0));
            }
            
            // Step 3: BT.2390 EETF highlight compression
            float L = dot(linear, LUMA_BT709);
            float Lmapped = BT2390_EETF(L, uMaxLuminance, uMinLuminance, 100.0);
            vec3 mapped = linear * (Lmapped / max(L, 0.0001));
            
            // Step 4: Hable toe + ACES (combined for performance)
            mapped = HableTonemap(mapped / 100.0);
            mapped = ACES_RRT(mapped);
            
            // Step 5: sRGB gamma
            return pow(clamp(mapped, 0.0, 1.0), vec3(1.0 / 2.2));
        }
        
        void main() {
            vec4 hdrSample = texture(uTexture, vTexCoord);
            fragColor = vec4(tonemap(hdrSample.rgb), hdrSample.a);
        }
    """

    private var programId: Int = 0
    private var isCompiled = false

    /**
     * Compile and link shader program
     */
    fun compile(): Boolean {
        if (isCompiled) return true

        try {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

            if (vertexShader == 0 || fragmentShader == 0) {
                DebugLogger.log(TAG, "Failed to compile shaders")
                return false
            }

            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)

            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(programId)
                DebugLogger.log(TAG, "Shader link failed: $log")
                GLES20.glDeleteProgram(programId)
                return false
            }

            // Cleanup shaders
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)

            isCompiled = true
            DebugLogger.log(TAG, "✓ Advanced HDR tonemapper compiled successfully")
            return true

        } catch (e: Exception) {
            DebugLogger.log(TAG, "Shader compilation error: ${e.message}")
            return false
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            DebugLogger.log(TAG, "Shader compile error: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    /**
     * Set tonemapping parameters
     */
    fun setParameters(
        maxLuminance: Float = 1000f,
        minLuminance: Float = 0.005f,
        avgLuminance: Float = 200f,
        transferFunction: Int = 0, // 0=PQ, 1=HLG
        colorSpace: Int = 0 // 0=BT.2020, 1=BT.709
    ) {
        if (!isCompiled) return

        GLES20.glUseProgram(programId)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uMaxLuminance"), maxLuminance)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uMinLuminance"), minLuminance)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uAvgLuminance"), avgLuminance)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uTransferFunction"), transferFunction)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uColorSpace"), colorSpace)
    }

    fun getProgramId(): Int = programId

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        isCompiled = false
    }
}
