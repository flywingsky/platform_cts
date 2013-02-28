/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
#include <math.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include "FullPipelineMesh.h"
#include "FullPipelineProgram.h"
#include "FullPipelineRenderer.h"
#include <graphics/Mesh.h>
#include <graphics/TransformationNode.h>
#include <GLUtils.h>

#define LOG_TAG "PTS_OPENGL"
#define LOG_NDEBUG 0
#include "utils/Log.h"

static const int FP_NUM_VERTICES = 6;

static const float FP_VERTICES[FP_NUM_VERTICES * 3] = {
        1.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        1.0f, 0.0f, 0.0f,
        1.0f, 1.0f, 0.0f };

static const float FP_NORMALS[FP_NUM_VERTICES * 3] = {
        0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 1.0f };

static const float FP_TEX_COORDS[FP_NUM_VERTICES * 2] = {
        1.0f, 1.0f,
        0.0f, 1.0f,
        0.0f, 0.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f };

static const char* FP_VERTEX =
        "uniform mat4 u_MVPMatrix;"
        "uniform mat4 u_MVMatrix;"
        "attribute vec4 a_Position;"
        "attribute vec3 a_Normal;"
        "attribute vec2 a_TexCoordinate;"
        "varying vec3 v_Position;"
        "varying vec3 v_Normal;"
        "varying vec2 v_TexCoordinate;"
        "void main() {\n"
        "  // Transform the vertex into eye space.\n"
        "  v_Position = vec3(u_MVMatrix * a_Position);\n"
        "  // Pass through the texture coordinate.\n"
        "  v_TexCoordinate = a_TexCoordinate;\n"
        "  // Transform the normal\'s orientation into eye space.\n"
        "  v_Normal = vec3(u_MVMatrix * vec4(a_Normal, 0.0));\n"
        "  // Multiply to get the final point in normalized screen coordinates.\n"
        "  gl_Position = u_MVPMatrix * a_Position;\n"
        "}";

static const char* FP_FRAGMENT =
        "precision mediump float;"
        "uniform vec3 u_LightPos;"
        "uniform sampler2D u_Texture;"
        "varying vec3 v_Position;"
        "varying vec3 v_Normal;"
        "varying vec2 v_TexCoordinate;"
        "void main() {\n"
        "  // Will be used for attenuation.\n"
        "  float distance = length(u_LightPos - v_Position);\n"
        "  // Get a lighting direction vector from the light to the vertex.\n"
        "  vec3 lightVector = normalize(u_LightPos - v_Position);\n"
        "  // Calculate the dot product of the light vector and vertex normal.\n"
        "  float diffuse = max(dot(v_Normal, lightVector), 0.0);\n"
        "  // Add attenuation.\n"
        "  diffuse = diffuse * (1.0 / (1.0 + (0.01 * distance)));\n"
        "  // Add ambient lighting\n"
        "  diffuse = diffuse + 0.25;\n"
        "  // Multiply the diffuse illumination and texture to get final output color.\n"
        "  gl_FragColor = (diffuse * texture2D(u_Texture, v_TexCoordinate));\n"
        "}";

FullPipelineRenderer::FullPipelineRenderer(ANativeWindow* window, int workload) :
        Renderer(window, workload), mProgram(NULL), mSceneGraph(NULL), mModelMatrix(NULL),
        mViewMatrix(NULL), mProjectionMatrix(NULL), mMesh(NULL) {
}

bool FullPipelineRenderer::setUp() {
    if (!Renderer::setUp()) {
        return false;
    }

    GLuint programId = GLUtils::createProgram(&FP_VERTEX, &FP_FRAGMENT);
    if (programId == 0)
        return false;
    mProgram = new FullPipelineProgram(programId);

    mModelMatrix = new Matrix();

    // Position the eye in front of the origin.
    float eyeX = 0.0f;
    float eyeY = 0.0f;
    float eyeZ = 2.0f;

    // We are looking at the origin
    float centerX = 0.0f;
    float centerY = 0.0f;
    float centerZ = 0.0f;

    // Set our up vector. This is where our head would be pointing were we holding the camera.
    float upX = 0.0f;
    float upY = 1.0f;
    float upZ = 0.0f;

    // Set the view matrix. This matrix can be said to represent the camera position.
    mViewMatrix = Matrix::newLookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);

    // Create a new perspective projection matrix. The height will stay the same
    // while the width will vary as per aspect ratio.
    float ratio = (float) width / height;
    float left = -ratio;
    float right = ratio;
    float bottom = -1.0f;
    float top = 1.0f;
    float near = 1.0f;
    float far = 3.0f;

    mProjectionMatrix = Matrix::newFrustum(left, right, bottom, top, near, far);

    // Setup texture.
    mTextureId = GLUtils::genRandTex(width, height);
    if (mTextureId == 0) {
        return false;
    }

    float count = pow(2, mWorkload - 1);
    float middle = count / 2.0f;
    float scale = 1.0f / count;

    mMesh = new Mesh(FP_VERTICES, FP_NORMALS, FP_TEX_COORDS, FP_NUM_VERTICES, mTextureId);
    mSceneGraph = new ProgramNode();

    for (int i = 0; i < count; i++) {
        for (int j = 0; j < count; j++) {
            Matrix* transformMatrix = Matrix::newScale(scale, scale, scale);
            transformMatrix->translate(i - middle, j - middle, 0.0f);
            TransformationNode* transformNode = new TransformationNode(transformMatrix);
            mSceneGraph->addChild(transformNode);
            FullPipelineMesh* meshNode = new FullPipelineMesh(mMesh);
            transformNode->addChild(meshNode);
        }
    }
    return true;
}

bool FullPipelineRenderer::tearDown() {
    if (mTextureId != 0) {
        glDeleteTextures(1, &mTextureId);
        mTextureId = 0;
    }
    if (!Renderer::tearDown()) {
        return false;
    }
    delete mModelMatrix;
    mModelMatrix = NULL;
    delete mViewMatrix;
    mViewMatrix = NULL;
    delete mProjectionMatrix;
    mProjectionMatrix = NULL;
    delete mProgram;
    mProgram = NULL;
    delete mSceneGraph;
    mSceneGraph = NULL;
    delete mMesh;
    mMesh = NULL;
    return true;
}

bool FullPipelineRenderer::draw(bool offscreen) {
    glBindFramebuffer(GL_FRAMEBUFFER, (offscreen) ? mFboId : 0);
    // Set the background clear color to black.
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    // Use culling to remove back faces.
    glEnable (GL_CULL_FACE);
    // Use depth testing.
    glEnable (GL_DEPTH_TEST);
    glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
    mModelMatrix->identity();
    mSceneGraph->draw(*mProgram, *mModelMatrix, *mViewMatrix, *mProjectionMatrix);

    GLuint err = glGetError();
    if (err != GL_NO_ERROR) {
        ALOGV("GLError %d", err);
        return false;
    }

    if (offscreen) {
        glFinish();
        return true;
    } else {
        return eglSwapBuffers(mEglDisplay, mEglSurface);
    }
}