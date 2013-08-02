/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.speechrecognition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import javaFlacEncoder.FLACEncoder;
import javaFlacEncoder.FLACStreamOutputStream;
import javaFlacEncoder.StreamConfiguration;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.media.AudioFormat;
import android.util.Log;

public class GoogleOnlineSpeechRecognizer extends SpeechRecognizer {

	private static final String API_URL = "http://www.google.com/speech-api/v1/recognize?client=chromium&lang=de-DE&maxresults=5";
	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_6_8) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.77 Safari/535.7";
	private static final int MAX_READ = 16384;

	public GoogleOnlineSpeechRecognizer() {
		super();
	}

	public void setWAVInputFile(String inputFilePath) throws IOException {
		setAudioInputStream(readWAVHeader(inputFilePath));
	}

	@Override
	public void run() {

		InputStream flacInputStream = startEncoding();
		if (flacInputStream == null) {
			return;
		}
		JSONArray jsonResponse = pipeToOnlineAPI(flacInputStream);

		ArrayList<String> matches = new ArrayList<String>();
		try {
			if (jsonResponse != null) {
				matches.add(jsonResponse.getJSONObject(0).getString("utterance"));
				for (int i = 1; i < jsonResponse.length(); i++) {
					matches.add(jsonResponse.getJSONObject(i).getString("utterance"));
				}
				sendResults(matches);
			}
		} catch (JSONException e) {
			sendError(ERROR_API_CHANGED, "The response JSON-Object couldn't be parsed correct");
		}
	}

	private InputStream startEncoding() {

		final FLACEncoder flac = new FLACEncoder();
		StreamConfiguration streamConfiguration = new StreamConfiguration();
		streamConfiguration.setBitsPerSample(stream.getSampleSizeInBits());
		streamConfiguration.setChannelCount(stream.getChannels());
		streamConfiguration.setSampleRate(stream.getSampleRate());

		flac.setStreamConfiguration(streamConfiguration);

		PipedInputStream pipedInputStream;
		final PipedOutputStream pipedOutputStream;
		FLACStreamOutputStream flacOutputStream;

		try {
			pipedOutputStream = new PipedOutputStream();
			flacOutputStream = new FLACStreamOutputStream(pipedOutputStream);
			flac.setOutputStream(flacOutputStream);
			pipedInputStream = new PipedInputStream(pipedOutputStream);
			flac.openFLACStream();
		} catch (IOException e1) {
			sendError(ERROR_OTHER, "Pipes couldn't be generated. Try filebased execution.");
			return null;
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Log.w("GoogleSpeechRecog", "Starting encoding...");
					encodeAudioInputStream(stream, MAX_READ, flac, true);
					Log.w("GoogleSpeechRecog", "Finished encoding...");
					pipedOutputStream.flush();
					pipedOutputStream.close();
				} catch (Exception e) {
					sendError(ERROR_OTHER, "There was a problem when converting into FLAC-Format.");
				}
			}
		}).start();

		return pipedInputStream;
	}

	private JSONArray pipeToOnlineAPI(InputStream speechInput) {

		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(API_URL);
		JSONArray resturnJson = new JSONArray();

		BasicHttpEntity entity = new BasicHttpEntity();
		entity.setChunked(true);
		entity.setContentLength(-1);
		entity.setContent(speechInput);

		httppost.setEntity(entity);
		httppost.setHeader("User-Agent", USER_AGENT);
		httppost.setHeader("Content-Type", "audio/x-flac; rate=16000;");

		HttpResponse response;
		try {
			Log.w("GoogleSpeechRecog", "Starting request...");
			response = httpclient.execute(httppost);
			Log.w("GoogleSpeechRecog", "Finished request...");
		} catch (ClientProtocolException cpe) {
			sendError(ERROR_NONETWORK, "Executing the postrequest failed.");
			return null;
		} catch (IOException e) {
			sendError(ERROR_NONETWORK, e.getMessage());
			return null;
		}

		if (response.getStatusLine().getStatusCode() != 200) {
			sendError(ERROR_API_CHANGED, "Statuscode was " + response.getStatusLine().getStatusCode());
			return null;
		}

		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
		} catch (IOException e) {
			sendError(ERROR_NONETWORK, e.getMessage());
			return null;
		}

		StringBuilder builder = new StringBuilder();
		try {
			for (String line = null; (line = reader.readLine()) != null;) {
				builder.append(line).append("\n");
			}
		} catch (IOException e) {
			sendError(ERROR_NONETWORK, e.getMessage());
			return null;
		}

		String resp = builder.toString();
		if (resp.contains("NO_MATCH")) {
			return resturnJson;
		}

		JSONObject object;
		try {
			object = (JSONObject) new JSONTokener(resp).nextValue();
			if (object.getInt("status") == 0) {
				resturnJson = object.getJSONArray("hypotheses");
			}
		} catch (JSONException e) {
			sendError(ERROR_API_CHANGED, "The response JSON-Object couldn't be parsed correct");
			return null;
		}
		return resturnJson;
	}

	@Override
	public void setAudioInputStream(AudioInputStream inputStream) throws IllegalArgumentException {
		if (inputStream.getSampleRate() != 16000) {
			throw new IllegalArgumentException("Unsupported SampleRate. Supported: 16kHz");
		}
		this.stream = inputStream;
	}

	private AudioInputStream readWAVHeader(String inputFilePath) throws IOException {

		long sampleRate;
		int sampleSizeInBits;
		int channels;

		FileInputStream fileStream = new FileInputStream(new File(inputFilePath));

		byte[] headerProperties = new byte[4];
		readBytes(fileStream, headerProperties, 4);
		if (headerProperties[0] != 'R' || headerProperties[1] != 'I' || headerProperties[2] != 'F'
				|| headerProperties[3] != 'F') {
			throw new IllegalArgumentException("Header mailformed or not supported.");
		}
		fileStream.skip(8);
		readBytes(fileStream, headerProperties, 4);
		if (headerProperties[0] != 'f' || headerProperties[1] != 'm' || headerProperties[2] != 't'
				|| headerProperties[3] != ' ') {
			throw new IllegalArgumentException("Header fmt-chunk not found.");
		}
		fileStream.skip(4);
		readBytes(fileStream, headerProperties, 4);
		channels = headerProperties[2];
		readBytes(fileStream, headerProperties, 4);
		sampleRate = byteToLong(headerProperties);
		fileStream.skip(4);
		readBytes(fileStream, headerProperties, 4);
		sampleSizeInBits = headerProperties[2];

		int encoding;
		if (sampleSizeInBits == 8) {
			encoding = AudioFormat.ENCODING_PCM_8BIT;
		} else if (sampleSizeInBits == 16) {
			encoding = AudioFormat.ENCODING_PCM_16BIT;
		} else {
			throw new IllegalArgumentException();
		}

		fileStream.close();

		AudioInputStream audioInputStream = new AudioInputStream(new FileInputStream(new File(inputFilePath)),
				encoding, channels, (int) sampleRate, 2, ByteOrder.LITTLE_ENDIAN, true);
		return audioInputStream;
	}

	private void readBytes(FileInputStream stream, byte[] buffer, int size) throws IOException {
		int readBytes = 0;
		while (readBytes != size) {
			int currentReadBytes = stream.read(buffer, readBytes, size - readBytes);
			if (currentReadBytes == -1) {
				throw new IllegalArgumentException();
			}
			readBytes += currentReadBytes;
		}
		return;
	}

	private long byteToLong(byte[] byteArray) {
		long result = 0;
		result = 0xff & byteArray[0];
		result |= ((long) (0xff & byteArray[1]) << 8);
		result |= ((long) (0xff & byteArray[2]) << 16);
		result |= ((long) (0xff & byteArray[3]) << 24);
		return result;

	}

	private int encodeAudioInputStream(AudioInputStream sin, int maxRead, FLACEncoder flac, boolean useThreads)
			throws IOException, IllegalArgumentException {
		int frameSize = sin.getFrameSize();
		int sampleSize = sin.getSampleSizeInBits();
		int bytesPerSample = sampleSize / 8;
		if (sampleSize % 8 != 0) {
			//end processing now
			throw new IllegalArgumentException("Unsupported Sample Size: size = " + sampleSize);
		}
		int channels = sin.getChannels();
		boolean bigEndian = sin.isBigEndian();
		boolean isSigned = sin.isSigned();
		byte[] samplesIn = new byte[maxRead];
		int samplesRead;
		int framesRead;
		int[] sampleData = new int[maxRead * channels / frameSize];
		int unencodedSamples = 0;
		int totalSamples = 0;
		while ((samplesRead = sin.read(samplesIn, 0, maxRead)) > 0) {
			framesRead = samplesRead / (frameSize);
			if (bigEndian) {
				for (int i = 0; i < framesRead * channels; i++) {
					int lower8Mask = 255;
					int temp = 0;
					int totalTemp = 0;
					for (int x = bytesPerSample - 1; x >= 0; x++) {
						int upShift = 8 * x;
						if (x == 0) {
							temp = ((samplesIn[bytesPerSample * i + x]) << upShift);
						} else {
							temp = ((samplesIn[bytesPerSample * i + x] & lower8Mask) << upShift);
						}
						totalTemp = totalTemp | temp;
					}
					if (!isSigned) {
						int reducer = 1 << (bytesPerSample * 8 - 1);
						totalTemp -= reducer;
					}
					sampleData[i] = totalTemp;
				}
			} else {
				for (int i = 0; i < framesRead * channels; i++) {
					int lower8Mask = 255;
					int temp = 0;
					int totalTemp = 0;
					for (int x = 0; x < bytesPerSample; x++) {
						int upShift = 8 * x;
						if (x == bytesPerSample - 1 && isSigned) {
							temp = ((samplesIn[bytesPerSample * i + x]) << upShift);
						} else {
							temp = ((samplesIn[bytesPerSample * i + x] & lower8Mask) << upShift);
						}
						totalTemp = totalTemp | temp;
					}
					if (!isSigned) {
						int reducer = 1 << (bytesPerSample * 8 - 1);
						totalTemp -= reducer;
					}
					sampleData[i] = totalTemp;
				}
			}
			if (framesRead > 0) {
				flac.addSamples(sampleData, framesRead);
				unencodedSamples += framesRead;
			}

			if (useThreads) {
				unencodedSamples -= flac.t_encodeSamples(unencodedSamples, false, 5);
			} else {
				unencodedSamples -= flac.encodeSamples(unencodedSamples, false);
			}
			totalSamples += unencodedSamples;
		}
		totalSamples += unencodedSamples;
		if (useThreads) {
			unencodedSamples -= flac.t_encodeSamples(unencodedSamples, true, 5);
		} else {
			unencodedSamples -= flac.encodeSamples(unencodedSamples, true);
		}
		return totalSamples;
	}
}
