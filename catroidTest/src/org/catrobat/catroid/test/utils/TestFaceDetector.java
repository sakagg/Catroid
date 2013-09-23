package org.catrobat.catroid.test.utils;

import org.catrobat.catroid.facedetection.FaceDetector;

public class TestFaceDetector extends FaceDetector {

	public boolean started = false;

	@Override
	public boolean startFaceDetection() {
		started = true;
		return true;
	}

	@Override
	public void stopFaceDetection() {
		started = false;
	}

	public void sendFaceDetected(boolean detected) {
		this.onFaceDetected(detected);
	}

}