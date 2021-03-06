package de.hsa.jam.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

/**
 * jAM helper class<br />
 * There are digital signal processing utils, some math utils and some other utils like printing a vector etc.
 *
 * @author Michael Wager
 */
public class jAMUtils {
	static String s = "";
	
	/*1. --------- some signal processing utils ----------*/
	
	//from SignalPowerExtractor (tarsos)
	public static boolean isSilence(final float[] buffer, float silenceThreshold) {
		double tmp = soundPressureLevel(buffer);
		
//		final float silenceThreshold = MINIMUM_LEVEL; //(float)Configuration.getDouble(ConfKey.silence_threshold); //--> gibt -70.0
		
//		if(tmp < silenceThreshold && !model.isEvaluating())
//				System.out.println("SILENCE: " + tmp + " < " + silenceThreshold);
		
		return  tmp < silenceThreshold;
	}
	public static double localEnergy(final float[] buffer) {
		double power = 0.0D;
		for (float element : buffer) {
			power += element * element;
		}
		return power;
	}
	
	/**
	 * return 20.0 * Math.log10(value)
	 * */
	private static double linearToDecibel(final double value) {
		return 20.0 * Math.log10(value);
	}

	public static double soundPressureLevel(final float[] buffer) {
		double value = Math.pow(localEnergy(buffer), 0.5);
		value = value / buffer.length;
		// System.out.println("LEVEL VAL: " + value + " --> " +
		// linearToDecibel(value) + "dB");
		return value;
		// return linearToDecibel(value);
	}

	// Computes the RMS volume of a group of signal sizes ranging from -1 to 1.
	public static double volumeRMS(float[] raw) {
		double sum = 0d;
		if (raw.length == 0) {
			return sum;
		} else {
			for (int ii = 0; ii < raw.length; ii++) {
				sum += raw[ii];
			}
		}
		double average = sum / raw.length;

		double[] meanSquare = new double[raw.length];
		double sumMeanSquare = 0d;
		for (int ii = 0; ii < raw.length; ii++) {
			sumMeanSquare += Math.pow(raw[ii] - average, 2d);
			meanSquare[ii] = sumMeanSquare;
		}
		double averageMeanSquare = sumMeanSquare / raw.length;
		double rootMeanSquare = Math.pow(averageMeanSquare, 0.5d);

		return rootMeanSquare;
	}

	/**
	 * Root mean square
	 * */
	public static float calculateRMSLevel(byte samples[], float samplesFloat[]) {
		float lSum = 0;
		for (int i = 0; i < samples.length; i++)
			lSum += (samples[i] * samples[i]);// / (float)samples.length;

		float rms = (float) Math.sqrt(lSum / (float) samples.length);

//		double rms2 = soundPressureLevel(samplesFloat); // TODO
		// System.out.println(rms2);
		// System.out.println(linearToDecibel(rms2));
		//
		// System.out.println(volumeRMS(samplesFloat));

		return rms;
	}

	/**
	 * simple fft calculation from http://jvalentino2.tripod.com/dft/index.html
	 * */
	public void fft(float[]x, float audioSampleRate, float overlap) {
		//Calculate the length in seconds of the sample
		float T = (x.length-overlap)/44100;
		System.out.println("T = "+T+ " (length of sampled sound in seconds)");

		//Calculate the number of equidistant points in time
		int n = x.length;//(int) (T * audioSampleRate) / 2;
		System.out.println("n = "+n+" (number of equidistant points)");

		//Calculate the time interval at each equidistant point
		float h = (T / n);
		System.out.println("h = "+h+" (length of each time interval in seconds)");
		
		//do the DFT for each value of x sub j and store as f sub j
		float f[] = new float[n/2];
		for (int j = 0; j < n/2; j++) {

			double firstSummation = 0;
			double secondSummation = 0;

			for (int k = 0; k < n; k++) {
		     		double twoPInjk = ((2 * Math.PI) / n) * (j * k);
		     		firstSummation +=  x[k] * Math.cos(twoPInjk);
		     		secondSummation += x[k] * Math.sin(twoPInjk);
			}

		        f[j] = (float)Math.abs( Math.sqrt(Math.pow(firstSummation,2) + 
		        Math.pow(secondSummation,2)) );

			double amplitude = 2 * f[j]/n;
			double frequency = j * h / T * audioSampleRate;
			
//			System.out.println("frequency = "+frequency+", amp = "+amplitude);
		}
//		fft.setData(f); //TODO fft
	}
	
	
	
	
	/*2. --------- some math utils ----------*/
	public static float[] zeros(int size) {
		float[] arr = new float[size];
		for (int i = 0; i < size; i++) {
			arr[i] = 0.0f;
		}
		return arr;
	}

	public static float[] ones(int size) {
		float[] arr = new float[size];
		for (int i = 0; i < size; i++) {
			arr[i] = 1.0f;
		}
		return arr;
	}

	public static float findMin(Vector<Integer> arr) {
		float min = Float.MAX_VALUE;
		for (int i = 0; i < arr.size(); i++) {
			if (arr.get(i) < min)
				min = arr.get(i);
		}
		return min;
	}

	public static float[] findMax(float[] arr) {
		float max = 0;
		float max_pos = 0;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] > max) {
				max_pos = i;
				max = arr[i];
			}
		}
		return new float[] { max_pos, max };
	}

	public static float[] findMax(Vector<Float> arr) {
		float max = 0;
		float max_pos = 0;
		for (int i = 0; i < arr.size(); i++) {
			if (arr.get(i) > max) {
				max_pos = i;
				max = arr.get(i);
			}
		}
		return new float[] { max_pos, max };
	}

	public static int findMax(Integer[] arr) {
		int max = 0;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] > max)
				max = arr[i];
		}
		return max;
	}

	public static int findMax(Object[] arr) {
		int max = 0;
		for (int i = 0; i < arr.length; i++) {
			if ((Integer) arr[i] > max)
				max = (Integer) arr[i];
		}
		return max;
	}

	public static byte findMax(byte[] arr) {
		byte max = 0;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] > max)
				max = arr[i];
		}
		return max;
	}

	public static float[] applyWindow(float[] data, float[] window) {
		float[] data_and_window = new float[window.length];

		for (int i = 0; i < window.length; i++) {
			data_and_window[i] = data[i] * window[i];
		}
		return data_and_window;
	}

	public static float[] applyWindow(byte[] data, float[] window) {
		float[] data_and_window = new float[window.length];

		for (int i = 0; i < window.length; i++) {
			data_and_window[i] = data[i] * window[i];
		}
		return data_and_window;
	}

	
	
	/**
	 * return [(index,min),(index,min)...]
	 * 
	 * from http://billauer.co.il/peakdet.html
	 * 
	 */
	public static Vector<double[]> detectExtremum(double[] data, float delta, boolean minima) {
		Vector<double[]>maxtab = new Vector<double[]>();
		Vector<double[]>mintab = new Vector<double[]>();
		
		double []x = new double[data.length];
		for (int i = 0; i < x.length; i++) {
			x[i] = i;
		}

		double mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
		double mnpos = 0, mxpos = 0;
		boolean lookformax = true;

		for (int i = 0; i < data.length; i++) {
		  double dis = data[i];
		  
		  if (dis > mx) { mx = dis; mxpos = x[i]; } 
		  if (dis < mn) { mn = dis; mnpos = x[i]; } 
		  
		  if (lookformax) {
		    if (dis < mx-delta) {
		    	maxtab.add(new double[] {mxpos, mx});
		      
		      mn = dis; 
		      mnpos = x[i];
		      lookformax = false;
		    }  
		  }else {
		    if (dis > mn+delta) {
		    	mintab.add(new double[] {mnpos, mn});
		      
		      mx = dis;
		      mxpos = x[i];
		      lookformax = true;
		    }
		 }
		}
		if(minima)
			return mintab;
		else
			return maxtab;
	}
	
	public static Vector<double[]> detectExtremum(Vector<Float>data, float delta, boolean minima) {
		double[] arr = new double[data.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = data.get(i);
		}
		return detectExtremum(arr, delta, minima);
	}
	public static Vector<double[]> detectLocalMinima(Vector<Float> data, float delta) {
		Vector<double[]>maxtab = new Vector<double[]>();
		Vector<double[]>mintab = new Vector<double[]>();
		
		double []x = new double[data.size()];
		for (int i = 0; i < x.length; i++) {
			x[i] = i;
		}

		double mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
		double mnpos = 0, mxpos = 0;
		boolean lookformax = true;

		for (int i = 0; i < data.size(); i++) {
		  double dis = data.get(i);
		  
		  if (dis > mx) { mx = dis; mxpos = x[i]; } 
		  if (dis < mn) { mn = dis; mnpos = x[i]; } 
		  
		  if (lookformax) {
		    if (dis < mx-delta) {
		    	maxtab.add(new double[] {mxpos, mx});
		      
		      mn = dis; 
		      mnpos = x[i];
		      lookformax = false;
		    }  
		  }else {
		    if (dis > mn+delta) {
		    	mintab.add(new double[] {mnpos, mn});
		      
		      mx = dis;
		      mxpos = x[i];
		      lookformax = true;
		    }
		  
		 }
		}
		
		return mintab;
	}
	
	//test detectLocalMinima()
//	public static void main(String[] args) {
//		double[]data={52.118134f, 54.120495, 53.990387, 52.803837, 53.729107, 53.583324, 52.302757, 52.046764, 53.970497, 50.07545, 38.652588, 33.35537, 25.502777, 21.26052, 51.785812, 53.477795, 53.5727, 54.11376, 52.597244, 51.38921, 52.81173, 53.83805, 51.74179, 54.77976, 52.835175, 53.412212, 53.763905, 52.61052, 53.597725, 53.750614, 55.747536, 53.758163, 52.89186, 52.90404, 52.901943, 52.42108, 54.211094, 52.655518, 54.21983, 53.80618, 53.27672, 52.864742, 53.101593, 52.94726, 51.990726, 52.056175, 53.680023, 52.84217, 53.363422, 51.972782, 52.380436, 53.95393, 48.214516, 52.426315, 43.007736, 42.808514, 49.357582, 48.394196, 52.278507, 51.252056, 52.713455, 54.71862, 54.465443, 53.76953, 54.067287, 54.07796, 52.705227, 53.901897, 53.11065, 53.385635, 53.2986, 53.645714, 52.99193, 54.14418, 52.64352, 51.536015, 53.533096, 53.62805, 53.563885, 53.15666, 53.77076, 52.404335, 53.95404, 53.41762, 53.859478, 53.980946, 52.91055, 53.53849, 53.133144, 54.409508, 52.305626, 53.515667, 53.970585, 53.854614, 53.568443, 52.688976, 53.850876, 53.079456, 53.337536, 53.873306, 52.294678, 52.54383, 52.655754, 52.173584, 53.341076, 51.718636, 52.48456, 53.979107, 51.28192, 52.00719, 53.102436, 53.170082, 52.160763, 53.935444, 52.198017, 53.192448, 52.54765, 52.01716, 53.072685, 53.830845, 52.947617, 53.55537, 52.72941, 53.1581, 53.2168, 51.813763, 52.370617, 53.498215, 51.813, 52.67684, 53.248623, 52.23544, 51.95006, 53.440407, 53.050655, 52.214558, 52.806934, 51.659264, 52.65783, 52.57798, 53.78034, 52.235916, 51.7738, 52.52781, 52.159668, 52.257416, 53.39784, 51.401752, 52.323875, 52.704826, 52.342827, 52.807354, 51.340244, 52.09482, 53.14098, 51.776, 53.213913, 53.556973, 52.5467, 53.518852, 51.619617, 53.489952, 49.068207, 44.79599, 29.019104, 19.64395, 51.911312};
//		
//		Vector<double []>erg = jAMUtils.detectLocalMinima(data, 10);
//		System.out.print("[");
//		for (int i = 0; i < erg.size(); i++) {
//			System.out.print("(" + erg.get(i)[0] + "," + erg.get(i)[1] + "), ");
//		}
//		System.out.println();
//		
//	}
	
	
	
	
	
	/*3. --------- Abc Notation utils ----------*/
	public static boolean inTonleiter(int midiKey, int[]tonleiter) {
		int noteIndex = midiKey % 12; // dann von 0-11

		for (int i = 0; i < tonleiter.length; i++) {
			if (noteIndex == tonleiter[i])
				return true;
		}
		return false;
	}
	
	/** 
	 * Array holds 128 sharp abc-notenames, to convert from midiKey to abc-note 
	 **/
	public static final String[] ABC_NOTES_SHARP = { 
			"C,,,,,", "^C,,,,,",
			"D,,,,,",
			"^D,,,,,",
			"E,,,,,",
			"F,,,,,",
			"^F,,,,,",
			"G,,,,,",
			"^G,,,,,",
			"A,,,,,",
			"^A,,,,,",
			"B,,,,,", // octave -1
			"C,,,,", "^C,,,,", "D,,,,", "^D,,,,",
			"E,,,,",
			"F,,,,",
			"^F,,,,",
			"G,,,,",
			"^G,,,,",
			"A,,,,",
			"^A,,,,",
			"B,,,,", // octave 0
			"C,,,", "^C,,,", "D,,,", "^D,,,", "E,,,",
			"F,,,",
			"^F,,,",
			"G,,,",
			"^G,,,",
			"A,,,",
			"^A,,,",
			"B,,,", // octave 1
			"C,,", "^C,,", "D,,", "^D,,", "E,,", "F,,",
			"^F,,",
			"G,,",
			"^G,,",
			"A,,",
			"^A,,",
			"B,,", // octave 2
			"C,", "^C,", "D,", "^D,", "E,", "F,", "^F,",
			"G,",
			"^G,",
			"A,",
			"^A,",
			"B,", // octave 3
			"C", "^C", "D", "^D", "E", "F", "^F", "G",
			"^G",
			"A",
			"^A",
			"B", // octave 4
			"c", "^c", "d", "^d", "e", "f", "^f", "g",
			"^g",
			"a",
			"^a",
			"b", // octave 5
			"c'", "^c'", "d'", "^d'", "e'", "f'", "^f'", "g'",
			"^g'",
			"a'",
			"^a'",
			"b'", // octave 6
			"c''", "^c''", "d''", "^d''", "e''", "f''", "^f''", "g''", "^g''",
			"a''",
			"^a''",
			"b''", // octave 7
			"c'''", "^c'''", "d'''", "^d'''", "e'''", "f'''", "^f'''", "g'''",
			"^g'''", "a'''", "^a'''",
			"b'''", // octave 8
			"c''''", "^c''''", "d''''", "^d''''", "e''''", "f''''", "^f''''",
			"g''''" // octave 9
	};

	/** 
	 * Array holds 128 flat abc-notenames, to convert from midiKey to abc-note 
	 **/
	public static final String[] ABC_NOTES_FLAT = { "C,,,,,", "_D,,,,,",
			"D,,,,,",
			"_E,,,,,",
			"E,,,,,",
			"F,,,,,",
			"_G,,,,,",
			"G,,,,,",
			"_A,,,,,",
			"A,,,,,",
			"_B,,,,,",
			"B,,,,,", // octave -1 (0-11)
			"C,,,,", "_D,,,,", "D,,,,", "_E,,,,",
			"E,,,,",
			"F,,,,",
			"_G,,,,",
			"G,,,,",
			"_A,,,,",
			"A,,,,",
			"_B,,,,",
			"B,,,,", // octave 0 (12-23)
			"C,,,", "_D,,,", "D,,,", "_E,,,", "E,,,",
			"F,,,",
			"_G,,,",
			"G,,,",
			"_A,,,",
			"A,,,",
			"_B,,,",
			"B,,,", // octave 1 (24-35)
			"C,,", "_D,,", "D,,", "_E,,", "E,,", "F,,",
			"_G,,",
			"G,,",
			"_A,,",
			"A,,",
			"_B,,",
			"B,,", // octave 2 (36-47) ab C,, == 36! !!!
			"C,", "_D,", "D,", "_E,", "E,", "F,", "_G,",
			"G,",
			"_A,",
			"A,",
			"_B,",
			"B,", // octave 3 (48-59)
			"C", "_D", "D", "_E", "E", "F", "_G", "G",
			"_A",
			"A",
			"_B",
			"B", // octave 4 (60-71)
			"c", "_d", "d", "_e", "e", "f", "_g", "g",
			"_a",
			"a",
			"_b",
			"b", // octave 5 (72-83)
			"c'", "_d'", "d'", "_e'", "e'", "f'", "_g'", "g'",
			"_a'",
			"a'",
			"_b'",
			"b'", // octave 6 (84-95)
			"c''", "_d''", "d''", "_e''", "e''", "f''", "_g''", "g''", "_a''",
			"a''",
			"_b''",
			"b''", // octave 7 (96-107) bis c'' == 96
			"c'''", "_d'''", "d'''", "_e'''", "e'''", "f'''", "_g'''", "g'''",
			"_a'''", "a'''", "_b'''",
			"b'''", // octave 8 (108-119)
			"c''''", "_d''''", "d''''", "_e''''", "e''''", "f''''", "_g''''",
			"g''''" // octave 9 (120-127)
	};

	
	
	
	
	
	
	
	
	/*4. --------- other utils ----------*/
	public static void sleep(int millisecs) {
		try {
			Thread.sleep(millisecs);
		} catch (Exception e) {
		}
	}

	public static void printArray(Vector<Float> v) {
		for (int i = 0; i < v.size(); i++) {
			System.out.print(v.get(i) + ",");
		}
		System.out.println();
	}

	public static void printArray(float[] arr) {
		for (int i = 0; i < arr.length; i++) {
			System.out.print(arr[i] + (i < arr.length - 1 ? "," : ""));
		}
		System.out.println();
	}

	public static void printArray(double[] arr) {
		for (int i = 0; i < arr.length; i++) {
			System.out.print(arr[i] + ",");
		}
		System.out.println();
	}

	public static void printArray(byte[] arr) {
		for (int i = 0; i < arr.length; i++) {
			System.out.print(arr[i] + ", ");
		}
		System.out.println();
	}

	public static void printArray(int[] arr) {
		for (int i = 0; i < arr.length; i++) {
			System.out.print(arr[i] + ", ");
		}
		System.out.println();
	}

	public static <Float, Integer> Set<Float> getKeysByValue(Map<Float, Integer> map, Integer value) {
		Set<Float> keys = new HashSet<Float>();
		for (Entry<Float, Integer> entry : map.entrySet()) {
			if (entry.getValue().equals(value)) {
				keys.add(entry.getKey());
			}
		}
		return keys;
	}

	
	
	/*************************************************************************************
	 private static String readFile(File file) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			// BufferedReader br = new BufferedReader(new InputStreamReader(new
			// DataInputStream(new FileInputStream(file))));
			String ret = "", current = "";
			while ((current = br.readLine()) != null) {
				ret += current + "\n";
			}
			return ret;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}
	
	static String trainingPath = "/Users/fred/Desktop/INFO/Studium/Semester7/Methoden_Der_KI/ki_material/weka/weka-3-6-4/data/TEST.arff";
	static String testPath = "/Users/fred/Desktop/INFO/Studium/Semester7/Methoden_Der_KI/ki_material/weka/weka-3-6-4/data/TESTSET.arff";

	static String trainingPathSVM = "/Volumes/Data/HOME/ECLIPSE/ECLIPSE_WORKSPACE/KI_TESTS/src/train";
	static String testPathSVM = "/Volumes/Data/HOME/ECLIPSE/ECLIPSE_WORKSPACE/KI_TESTS/src/test";

	public static void initARFFFile(int anzahlAttributes, boolean training) {
		try {
			String str = "";
			FileWriter out = null;

			if (training)
				out = new FileWriter(new File(trainingPath));
			else
				out = new FileWriter(new File(testPath));

			// CREATE ARFF FILE
			out.write("@RELATION notes\n");
			out.write("\n");

			for (int i = 0; i < anzahlAttributes; i++) {
				str = "@ATTRIBUTE attr" + i + "    REAL";
				out.write(str + "\n");
			}
			out.write("@ATTRIBUTE class 	{A2,A3,C#3,C#4,D#3,D2,D3,D5,E2,E3,E4,E5,F#2,F#3,F#4,G#4,G2,G3,G5,H2,H3,H4}\n\n");// {E2,A2,D3,G3,H3,E4}

			out.write("@DATA\n");

			out.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void appendARFFFile(float[] arr, String klasse,
			boolean training) {
		// System.out.println(arr.length);

		try {
			// String lineToAdd="";
			File file = null;
			if (training)
				file = new File(trainingPath);
			else
				file = new File(testPath);

			// first read content!
			// String content = readFile(file);
			// System.out.println("appending arff.....");
			// FileWriter out = new FileWriter(file); //leert die Datei???

			for (int i = 0; i < arr.length; i++) {
				// lineToAdd += arr[i] + (i<arr.length-1 ? "," : "");
				s += arr[i] + (i < arr.length - 1 ? "," : "");
			}

			s += "," + klasse + "\n";

			// System.out.println("WRITING NOW!");
			// out.append("\n" + lineToAdd + "," + klasse);
			// out.write(content + lineToAdd + "," + klasse);
			// out.close();

			// System.out.println("APPENDED ARFF");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void writeAll() {
		try {
			File file = new File(trainingPath);
			FileWriter out = new FileWriter(file); // leert die Datei???

			String content = readFile(file);
			out.write(content + s);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void appendSVMFile(int klasse, float[] arr, boolean training) {

		// ZEILE LIKE DIS:
		// 1 1:2.617300e+01 2:5.886700e+01 3:-1.894697e-01 4:1.251225e+02 -->
		// Klasse 1, 4 Attribute mit labels
		try {
			String str = "";
			File file = null;
			if (training)
				file = new File(trainingPathSVM);
			else
				file = new File(testPathSVM);

			// first read content!
			String content = readFile(file);

			FileWriter out = new FileWriter(file); // leert die Datei???
			for (int i = 0; i < arr.length; i++) {
				str += (i + 1) + ":" + arr[i] + " ";
			}

			out.write(content + klasse + " " + str);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}


	public static void writeToFile(byte[] arr, int start, int end)
			throws Exception {
		FileWriter w = new FileWriter(new File("/Users/fred/Desktop/test.txt"));
		String str = "";
		for (int i = start; i < end; i++) {
			str += arr[i];
			if (i < arr.length - 1)
				str += ",";
		}
		w.write("------------------------------------------------------------\n"
				+ str);
		w.flush();
		w = null;
		// System.out.println("WRITTEN");
	}

	public static void writeToFile(float[] arr, int start, int end)
			throws Exception {
		FileWriter w = new FileWriter(new File("/Users/fred/Desktop/test.txt"));
		String str = "";
		for (int i = start; i < end; i++) {
			str += arr[i];
			if (i < arr.length - 1)
				str += ",";
		}
		w.write("------------------------------------------------------------\n"
				+ str);
		w.flush();
		w = null;
	}

	
	
	 *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ****/
	
	// public static void main(String[] args) {

	// System.out.println(MathUtils.readFile(new File(testPath)));
	// MathUtils.appendARFFFile(new float[]{1,2}, "A", true);
	// MathUtils.appendARFFFile(new float[]{3,4}, "A", true);
	// }
	// public static void main(String[] args) throws Exception{
	// // float [] arr = MathUtils.zeros(10);
	// // for (int i = 0; i < arr.length; i++) {
	// // System.out.println(arr[i] + ", ");
	// // }
	//
	// // Vector<Integer> a = new Vector<Integer>();
	// // a.add(7);a.add(2); a.add(3);
	// a.add(4);a.add(4);a.add(4);a.add(4);a.add(6);a.add(2);
	// //
	// // System.out.println(MathUtils.findMin(a));
	//
	// // System.out.println("OK");
	// // MathUtils.writeToFile("/Users/fred/Desktop/test.txt",
	// "HALLdddO 1\nHallo 2");
	//
	//
	// // HashMap<Float, Integer> map = new HashMap<Float, Integer>();
	// // map.put(440.123f, 3);
	// // System.out.println(MathUtils.getKeysByValue(map, 3).toArray()[0]);
	// //
	// byte [] arr = new byte[1024];
	// for (int i = 0; i < arr.length; i++) {
	// arr[i] = 12;
	// }
	// MathUtils.writeToFile(arr, 0, 1024);
	//
	//
	// }
}