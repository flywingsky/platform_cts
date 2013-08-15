/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.camera2.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.Arrays;

/**
 * <p>Basic test for CameraManager class.</p>
 */
public class CameraManagerTest extends AndroidTestCase {
    private static final String TAG = "CameraManagerTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int NUM_CAMERA_REOPENS = 10;

    private PackageManager mPackageManager;
    private CameraManager mCameraManager;
    private NoopCameraListener mListener;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mCameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Can't connect to camera manager", mCameraManager);
        mPackageManager = context.getPackageManager();
        assertNotNull("Can't get package manager", mPackageManager);
        mListener = new NoopCameraListener();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testCameraManagerGetDeviceIdList() throws Exception {

        // Test: that the getDeviceIdList method runs without exceptions.
        String[] ids = mCameraManager.getDeviceIdList();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(ids));

        // Test: that if the device has a camera, there must be at least one reported id.
        assertTrue("At least one camera must be detected",
               ! mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            || ids.length >= 1);

        /**
         * Test: that if the device has both front and rear facing cameras, then there
         * must be at lest two reported ids.
         */
        assertTrue("At least two cameras must be detected",
               ! mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
            || ! mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
            || ids.length >= 2);

        /**
         * Test: that if there is one camera device, then the system must have some
         * specific features.
         */
        assertTrue("Missing system feature: FEATURE_CAMERA_ANY",
               ids.length == 0
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
        assertTrue("Missing system feature: FEATURE_CAMERA or FEATURE_CAMERA_FRONT",
               ids.length == 0
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
    }

    // Test: that properties can be queried from each device, without exceptions.
    public void testCameraManagerGetCameraProperties() throws Exception {
        String[] ids = mCameraManager.getDeviceIdList();
        for (int i = 0; i < ids.length; i++) {
            CameraDevice camera = mCameraManager.openCamera(ids[i]);
            assertNotNull(
                String.format("Failed to open camera device ID: %s", ids[i]), camera);
            try {
                CameraProperties props = camera.getProperties();
                assertNotNull(
                    String.format("Can't get camera properties from: ID %s", ids[i]), props);
            }
            finally {
                camera.close();
            }
        }
    }

    // Test: that an exception is thrown if an invalid device id is passed down.
    public void testCameraManagerInvalidDevice() throws Exception {
        String[] ids = mCameraManager.getDeviceIdList();
        // Create an invalid id by concatenating all the valid ids together.
        StringBuilder invalidId = new StringBuilder();
        invalidId.append("INVALID");
        for (int i = 0; i < ids.length; i++) {
            invalidId.append(ids[i]);
        }
        try {
            CameraProperties props = mCameraManager.getCameraProperties(
                invalidId.toString());
            fail(String.format("Accepted invalid camera ID: %s", invalidId.toString()));
        }
        catch (IllegalArgumentException e) {
            // This is the exception that should be thrown in this case.
        }
    }

    // Test: that each camera device can be opened one at a time, several times.
    public void testCameraManagerOpenCamerasSerially() throws Exception {
        String[] ids = mCameraManager.getDeviceIdList();
        for (int i = 0; i < ids.length; i++) {
            for (int j = 0; j < NUM_CAMERA_REOPENS; j++) {
                CameraDevice camera = mCameraManager.openCamera(ids[i]);
                assertNotNull(
                    String.format("Failed to open camera device ID: %s", ids[i]), camera);
                camera.close();
            }
        }
    }

    /**
     * Test: that all camera devices can be open at the same time, or the appropriate
     * exception is thrown if this can't be done.
     */
    public void testCameraManagerOpenAllCameras() throws Exception {
        String[] ids = mCameraManager.getDeviceIdList();
        CameraDevice[] cameras = new CameraDevice[ids.length];
        try {
            for (int i = 0; i < ids.length; i++) {
                try {
                    cameras[i] = mCameraManager.openCamera(ids[i]);

                    /**
                     * If the camera can't be opened, should throw an exception, rather than
                     * returning null.
                     */
                    assertNotNull(
                        String.format("Failed to open camera device ID: %s", ids[i]),
                        cameras[i]);
                }
                catch (CameraAccessException e) {
                    /**
                     * This is the expected behavior if the camera can't be opened due to
                     * limitations on how many devices can be open simultaneously.
                     */
                    assertEquals(
                        String.format("Invalid exception reason: %s", e.getReason()),
                        CameraAccessException.MAX_CAMERAS_IN_USE, e.getReason());
                }
            }
        }
        finally {
            for (int i = 0; i < ids.length; i++) {
                if (cameras[i] != null) {
                    cameras[i].close();
                }
            }
        }
    }

    // Test: that opening the same device multiple times throws the right exception.
    public void testCameraManagerOpenCameraTwice() throws Exception {
        String[] ids = mCameraManager.getDeviceIdList();
        CameraDevice[] cameras = new CameraDevice[2];
        if (ids.length > 0) {
            try {
                cameras[0] = mCameraManager.openCamera(ids[0]);
                assertNotNull(
                    String.format("Failed to open camera device ID: %s", ids[0]),
                    cameras[0]);
                try {
                    cameras[1] = mCameraManager.openCamera(ids[0]);
                    fail(String.format("Opened the same camera device twice ID: %s",
                        ids[0]));
                }
                catch (CameraAccessException e) {
                    /**
                     * This is the expected behavior if the camera device is attempted to
                     * be opened more than once.
                     */
                    assertEquals(
                        String.format("Invalid exception reason: %s", e.getReason()),
                        CameraAccessException.CAMERA_IN_USE, e.getReason());
                }
            }
            finally {
                for (int i = 0; i < 2; i++) {
                    if (cameras[i] != null) {
                        cameras[i].close();
                    }
                }
            }
        }
    }

    private class NoopCameraListener implements CameraManager.CameraListener {
        @Override
        public void onCameraAvailable(String cameraId) {
            // No-op
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            // No-op
        }
    }

    /**
     * Test: that the APIs to register and unregister a listener run successfully;
     * doesn't test that the listener actually gets invoked at the right time.
     * Registering a listener multiple times should have no effect, and unregistering
     * a listener that isn't registered should have no effect.
     */
    public void testCameraManagerListener() throws Exception {
        mCameraManager.unregisterCameraListener(mListener);
        mCameraManager.registerCameraListener(mListener);
        mCameraManager.registerCameraListener(mListener);
        mCameraManager.unregisterCameraListener(mListener);
        mCameraManager.unregisterCameraListener(mListener);
    }
}
