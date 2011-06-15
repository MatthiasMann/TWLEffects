/*
 * Copyright (c) 2008-2011, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.twleffects.lwjgl;

import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.renderer.AnimationState;
import de.matthiasmann.twl.renderer.Image;
import de.matthiasmann.twl.renderer.OffscreenSurface;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.EXTFramebufferObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

/**
 *
 * @author Matthias Mann
 */
public class LWJGLOffscreenSurface implements OffscreenSurface {
    
    private final LWJGLEffectsRenderer renderer;
    
    private int fboID;
    private int textureID;
    private int textureWidth;
    private int textureHeight;
    private int usedWidth;
    private int usedHeight;
    private boolean bound;

    LWJGLOffscreenSurface(LWJGLEffectsRenderer renderer) {
        this.renderer = renderer;
    }

    boolean allocate(int width, int height) {
        if(fboID == 0) {
            fboID = glGenFramebuffersEXT();
        }
        bindFBO();
        if(width > textureWidth || height > textureHeight) {
            if(textureID == 0) {
                textureID = glGenTextures();
            }
            textureWidth = nextPowerOf2(width);
            textureHeight = nextPowerOf2(height);
            glBindTexture(GL_TEXTURE_2D, textureID);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);  
            glTexImage2D(GL_TEXTURE_2D, 0,
                    GL_RGBA8, textureWidth, textureHeight, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, (ByteBuffer)null);
            glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT,
                    GL_COLOR_ATTACHMENT0_EXT,
                    GL_TEXTURE_2D, textureID, 0);
            int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
            if(status != GL_FRAMEBUFFER_COMPLETE_EXT) {
                System.out.println(status);
            }
        }
        usedWidth = width;
        usedHeight = height;
        return true;
    }
    
    private int nextPowerOf2(int i) {
        return Integer.highestOneBit(i - 1) << 1;
    }
    
    void bindFBO() {
        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, fboID);
        bound = true;
    }

    void unbindFBO() {
        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0);
        bound = false;
    }

    void checkNotBound() {
        if(bound) {
            throw new IllegalStateException("offscreen rendering active");
        }
    }
    
    public void destroy() {
        checkNotBound();
        if(fboID != 0) {
            glDeleteFramebuffersEXT(fboID);
            fboID = 0;
        }
        if(textureID != 0) {
            glDeleteTextures(textureID);
            textureID = 0;
        }
        textureWidth = 0;
        textureHeight = 0;
        usedWidth = 0;
        usedHeight = 0;
    }

    public Image createTintedVersion(Color color) {
        if(color.equals(Color.WHITE)) {
            return this;
        } else {
            return createTinted(color);
        }
    }

    public void draw(AnimationState as, int x, int y) {
        draw(as, x, y, usedWidth, usedHeight);
    }

    public void draw(AnimationState as, int x, int y, int w, int h) {
        draw(Color.WHITE, x, y, w, h);
    }
    
    void draw(Color color, int x, int y, int w, int h) {
        checkNotBound();
        if(textureID == 0) {
            return;
        }
        float tx1 = usedWidth / (float)textureWidth;
        float ty0 = 0.0f;
        float ty1 = usedHeight / (float)textureHeight;
        
        renderer.setColor(color);
        
        glBindTexture(GL_TEXTURE_2D, textureID);
        glBlendFunc(GL_ONE, GL_SRC_ALPHA);
        glBegin(GL_QUADS);
        glTexCoord2f(  0, ty1); glVertex2i(x    , y    );
        glTexCoord2f(  0, ty0); glVertex2i(x    , y + h);
        glTexCoord2f(tx1, ty0); glVertex2i(x + w, y + h);
        glTexCoord2f(tx1, ty1); glVertex2i(x + w, y    );
        glEnd();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public int getWidth() {
        return usedWidth;
    }

    public int getHeight() {
        return usedHeight;
    }
    
    Image createTinted(final Color color) {
        return new Image() {
            public Image createTintedVersion(Color newColor) {
                if(newColor.equals(Color.WHITE)) {
                    return this;
                } else {
                    return createTinted(color.multiply(newColor));
                }
            }
            public void draw(AnimationState as, int x, int y) {
                LWJGLOffscreenSurface.this.draw(as, x, y);
            }
            public void draw(AnimationState as, int x, int y, int width, int height) {
                LWJGLOffscreenSurface.this.draw(as, x, y, width, height);
            }
            public int getWidth() {
                return LWJGLOffscreenSurface.this.getWidth();
            }
            public int getHeight() {
                return LWJGLOffscreenSurface.this.getHeight();
            }
        };
    }
}
