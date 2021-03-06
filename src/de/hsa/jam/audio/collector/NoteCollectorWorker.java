package de.hsa.jam.audio.collector;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

import abc.notation.Note;
import be.hogent.tarsos.sampled.pitch.McLeodPitchMethod;
import be.hogent.tarsos.sampled.pitch.Pitch;
import be.hogent.tarsos.sampled.pitch.PitchConverter;
import be.hogent.tarsos.sampled.pitch.PitchUnit;
import be.hogent.tarsos.sampled.pitch.Yin;

import de.hsa.jam.ControllerEngine;
import de.hsa.jam.jAM;
import de.hsa.jam.audio.Model;
import de.hsa.jam.evaluation.Evaluator;
import de.hsa.jam.ui.SimplePlotterFrame;
import de.hsa.jam.util.jAMUtils;

/**
 * This class implements the pipeline.<br />
 * First read a buffer from the queue. <br />
 * Then detect pitch, collect, pre-process and decide.<br />
 *  
 * @author Michael Wager
 */
public class NoteCollectorWorker implements Runnable {
	private Thread thread;

	private Model model; 
	private AudioBufferQueue queue;
	
	private boolean COLLECTING = false;

	// ---------- pitch detect -----
	private String PITCHDETECTOR = "";
	// different pitch detection algorithms:
	private Yin yin;
	private McLeodPitchMethod mpm;
	
	private float pitchInHertz;
	private Pitch pitch;
	private float pitch_probability;
	private float countMSprocessing = 0;

	private float YIN_TRESHOLD = 0.13f, // TODO das hier muss immer noch mit GUI �bereinstimen !!!
				  MPM_TRESHOLD = 0.93f;
	
	private int MINIMUM_DURATION = 50; // TODO das hier muss immer noch mit GUI �bereinstimen !!!
	private float MINIMUM_LEVEL = -70.0f ;//20;
	
	// Erkenntnisse von Evaluation:
	// MinDur/MinLevel: 40 30ms  UNBRAUCHBAR (fuer meine Testdaten..)
	//					40 10 auch
	//			 		50 30 auch
	//					
	
	/* TODO optimize:
	bei delta=5 gabs glaub ich 98% auf MIDI walde !!!
	vergleich mit "ohne" level shit und check warum dann 98% auf einmal??!?!?
	
	TODO delta==MINI_LEVEL ?
	wenn ich nach maxima suche wird 
	erst 0,0,0,1,12,13,12,12,12, 55  --> MAXIMA !
	
	dann: 0
	*/private int delta = 8; //local minima/maxima constraint threshold
	
	private float audioSampleRate;
	private int bufferSize, overlap, bpm, timeForOneBeat, timeFor16thNote;

	// ---------- noten malen: -----
	private String notesAsString = "";
	private int lenge = 0, takte = 0, OBEN = 4, UNTEN = 4;
	private boolean FLAT_KEY = false;

	private SimplePlotterFrame plotterYIN, plotterMPM, plotterBUFFER;

	private boolean ONSET = false, NEW_NOTE_ONSET = false;

	private SortedMap<Integer, Float> midiKeySammler; // um onset/offset -Bedingungen zu checken
	private Vector<Float[]> midiKeysAndLevels; // sammle Noten waehrend ONSET um  dann auszuwerten (und deren  rms-levels)
	private Vector<Integer> midiKeysRests; // sammle Pausen waehrend !ONSET um dann auszuwerten

	//TODO now ? level schon vorher checkn !!! also andere offset bedingungen!
	private Vector<Float> levels=new Vector<Float>();
	
	// es gibt den zuletzt erkannten und den zuletzt "gewaehlten"(taken)
	private int lastDetectedMidiKey = 0, lastTakenMidiKey = -1;

//	private float msToIgnore = 0;
	private int newNoteCount = 0;

	// ---------- STATS:
	private SortedMap<Integer, Float> midiKeySammlerInsgesamt;// sammle ALLE erkannten Noten(nur fuer stats)
	private float yin_cnt = 0, mpm_cnt = 0;

	// ---------- fuer evaluation
	private Vector<Integer> evaluationSammler;
	private Evaluator evaluator = null;
	
	private float countSamples=0;

	/** Model instantiates a new collectorWorker for each melody.
	 * 
	 * @param model - get Model to set ModelProperties and to communicate with view
	 * @param queue - the AudioBufferQueue
	 * @param audioSampleRate - samplerate
	 * @param bufferSize - buffersize in samples
	 * @param overlap - bufferoverlap in samples
	 * @param bpm - beats per minute of the recording
	 * @param PDA - which pitch detection algorithm to use: "YIN" or "MPM"
	 */
	public NoteCollectorWorker(Model model, AudioBufferQueue queue, float audioSampleRate, int bufferSize, int overlap, int bpm, String PDA) {
		this.model = model;
		this.queue = queue;

		this.PITCHDETECTOR = PDA;

		reset();
		this.bpm = bpm;
		initTime();

		this.audioSampleRate = audioSampleRate;
		this.bufferSize = bufferSize;
		this.overlap = overlap;

		yin = new Yin(audioSampleRate, bufferSize, YIN_TRESHOLD);
		mpm = new McLeodPitchMethod(audioSampleRate, bufferSize, MPM_TRESHOLD);

		if (!jAM.EVALUATING)
			jAM.log("Collector Constructor:  ==> timeForOneBeat: "
					+ timeForOneBeat + "ms fS: " + audioSampleRate + " CHUNK: "
					+ bufferSize + " OVERLAP: " + overlap + " BPM: " + bpm
					+ " PDA: " + PITCHDETECTOR + "", false);
	}

	private void reset() {
		if (queue != null)
			queue.clear();

		midiKeySammlerInsgesamt = new TreeMap<Integer, Float>();
		midiKeySammler = new TreeMap<Integer, Float>();
		notesAsString = "";
		midiKeysAndLevels = new Vector<Float[]>(); // darin wird gesammelt und
													// dann ausgewertet
		midiKeysRests = new Vector<Integer>();// darin wird gesammelt und dann
												// ausgewertet

		lastDetectedMidiKey = -1;
		lastTakenMidiKey = -1;

		evaluationSammler = new Vector<Integer>();
	}

	public boolean isCollecting() {
		return COLLECTING;
	}

	private String[] tonarten = new String[] { "C", "G", "D", "A", "E", "B",
			"F#", "F", "Bb", "Eb", "Ab", "Db", "C#", "Gb" };

	// TODO besserer Weg um zu checken ob Ton in Tonleiter valide ist!!!???
	private int[][] tonleitern = { { 0, 2, 4, 5, 7, 9, 11 }, // C-Dur
			{ 0, 2, 4, 6, 7, 9, 11 }, // G-Dur
			{ 1, 2, 4, 6, 7, 9, 11 }, // D-Dur
			{ 1, 2, 4, 6, 8, 9, 11 }, // A-Dur
			{ 1, 3, 4, 6, 8, 9, 11 }, // E-Dur
			{ 1, 3, 4, 6, 8, 10, 11 }, // B-Dur

			{ 1, 3, 4, 6, 8, 10, 11 }, // F#-Dur TODO
			{ 0, 2, 4, 5, 7, 9, 10 }, // F-Dur
			{ 0, 2, 4, 6, 7, 9, 11 }, // Bb-Dur TODO
			{ 0, 2, 4, 6, 7, 9, 11 }, // Eb-Dur TODO
			{ 0, 2, 4, 6, 7, 9, 11 }, // Ab-Dur TODO
			{ 0, 2, 4, 6, 7, 9, 11 }, // Db-Dur TODO
			{ 0, 2, 4, 6, 7, 9, 11 }, // C#-Dur TODO
			{ 0, 2, 4, 6, 7, 9, 11 } // Gb-Dur TODO
	};
	private int[] tonleiter = tonleitern[0];// default: C-Dur

	public void setTonart(int idx) {
		tonleiter = tonleitern[idx];
		this.FLAT_KEY = (tonarten[idx].contains("b") || tonarten[idx]
				.equals("F")) ? true : false;
		
	}

	public void setTaktart(int oben, int unten) {
		OBEN = oben;
		UNTEN = unten;
	}

	public void setYinTreshold(float t) {
		jAM.log("Collector: setYinTreshold() " + t, false);
		YIN_TRESHOLD = t;
		yin = new Yin(audioSampleRate, this.bufferSize, YIN_TRESHOLD);
	}

	public void setMpmTreshold(float t) {
		jAM.log("Collector: setMpmTreshold() " + t, false);
		MPM_TRESHOLD = t;
		mpm = new McLeodPitchMethod(audioSampleRate, bufferSize, MPM_TRESHOLD);
	}

	public void setMinDur(int d) {
		jAM.log("Collector: setMinDur() " + d, false);
		this.MINIMUM_DURATION = d;
	}

	public void setMinLev(float l) {
		jAM.log("Collector: setMinLev() " + l, false);
		this.MINIMUM_LEVEL = l;
	}

	public int getMinDur() {
		return MINIMUM_DURATION;
	}

	public float getMinLev() {
		return MINIMUM_LEVEL;
	}

	public void setQueue(AudioBufferQueue q) {
		this.queue = q;
	}

	public void setBPM(int bpm) {
		this.bpm = bpm;
		initTime();
	}

	private void initTime() {
		timeForOneBeat = 60000 / bpm;
		// timeFor8thNote = timeForOneBeat/2;
		timeFor16thNote = timeForOneBeat / 4;
	}

	public void setEvaluator(Evaluator eval) {
		this.evaluator = eval;
	}

	/**
	 * show some statistics at the end of the collector process 
	 **/
	public void showStatistics() {
		// TODO nur fuer mich die folgenden stats...haben in eval
		// erstmal! nix verloren
		String stat = "";
		if (!model.isEvaluating()) {
			stat += "\n****************************** START STATS ******************************\n";
			stat += "!ALL! DETECTED NOTES AND THEIR LENGTHS IN ms: (just for stats)\n";
			stat += midiKeySammlerInsgesamt.toString() + "\n\n";

			stat += "AbcNotes-Backup: \n" + notesAsString + "\n";

			stat += "YIN/MPM - STATS:\n";
			float sum = yin_cnt + mpm_cnt;
			stat += "YIN: " + yin_cnt / sum * 100 + "% - MPM: " + mpm_cnt / sum
					* 100 + "%\n";
			stat += "****************************** END STATS ******************************\n\n";
			jAM.log(stat, false);
		}else
			evaluator.evaluateCurrentTranscription(evaluationSammler, PITCHDETECTOR, notesAsString);
	}

	private void end(boolean initAfter) {
		COLLECTING = false;

		if (model.plottingSelected()) {
			plotterYIN.stop();
			plotterYIN.setVisible(false);
			plotterMPM.stop();
			plotterMPM.setVisible(false);
			plotterBUFFER.stop();
			plotterBUFFER.setVisible(false);
		}

		showStatistics();

		model.firePropertyChange(ControllerEngine.START_STOP_PROCESSING_BUTTON_PROPERTY, "stop","rec");
		model.firePropertyChange(ControllerEngine.INPUTLEVEL_PROPERTY, -1, 0);
		reset();

		if (initAfter && !model.isEvaluating())
			model.initProcessing(null);
	}

	public void stopCollecting(boolean fileProcessed) {
		if (thread != null)
			thread.interrupt();
		thread = null;

		end(fileProcessed);
	}

	private long START;

	public String timestamp() {
		//return (System.currentTimeMillis() - START) / 1000.0f + "s";
		String timestamp = String.format("%.4g", countSamples/audioSampleRate * 1000.0f);
		return timestamp + "ms";
	}

	/**
	 * start the collector thread 
	 **/
	public void start() {
		START = System.currentTimeMillis();

		if (!COLLECTING) {
			queue.clear();
			thread = new Thread(this);
			thread.start();
			COLLECTING = true;
		}
	}

//	private SimplePlotterFrame fft = new SimplePlotterFrame("FFT TEST", 100);//TODO fft
	/**
	 * in loop: get buffer from queue and call detectPitchAndCollect()
	 **/
	public void run() {
//		fft.start(); //TODO fft
		
		// System.out.println("starte collector, TIME: " +
		// (System.currentTimeMillis()-jAM.GLOBAL_TIMESTAMP) );

		if (model.plottingSelected()) {
			plotterYIN = new SimplePlotterFrame("YIN Buffer Plotter", 200);
			plotterYIN.start();
			plotterMPM = new SimplePlotterFrame("MPM", 400);
			plotterMPM.start();
			plotterBUFFER = new SimplePlotterFrame("Audio Buffer Plotter", 600);
			plotterBUFFER.start();
		}

		// detectionStartTime=System.currentTimeMillis();

		try {
			while (thread != null) {
				// long start = System.currentTimeMillis();
				// get data: block if queue is empty
				float[] audioFloatBuffer = queue.get();

				// check end-of-stream marker
				if (audioFloatBuffer == null) {
					// System.out.println("COLLECTOR:  buffer is null!");
					end(true);
					return;
				}
				
				double level = jAMUtils.soundPressureLevel(audioFloatBuffer);
				
				//TODO level (dB) to percent ?
//				System.out.println(Math.round(1000000000*Math.pow(10,level/20))/10000000 + "%");
				
				countSamples += audioFloatBuffer.length - overlap;
				

				// ----- ok wir haben nun einen buffer aus der queue geholt
				// -----
				detectPitchAndCollect(audioFloatBuffer, level);

				// TODO doc evaluation: auf intel 2 core blabla zB 3ms fuer
				// detectPitchAndCollect() -> diesen pipeline schritt
				// System.out.println("===TIME FOR ONE STEP===> " +
				// (System.currentTimeMillis()-start) + "ms");
			}
			// end();
		} catch (InterruptedException e) {
			// e.printStackTrace();
			System.err.println("catched interr exc in collector.run()");
		}
	}

	// ------------- collector-pipeline start --------------
	//detectPitchAndCollect() and collect() ??? TODO

	/**
	 * <b>This is the most important function of the collector pipeline</b>
	 * First we detect the pitch of the audioFloatBuffer using YIN or MPM.<br />
	 * Then the collector process starts, checking onsets and offsets based on midiKeys and dB, collects<br />
	 * and convert these pich vectors to abc-notes based on the bpm, samplerate buffersize and bufferoverlap.<br /> 
	 **/
	private void detectPitchAndCollect(float[] audioFloatBuffer, double level) {
		int midiKey;
		String note;

		// TODO
		pitchInHertz = getBestPitch(audioFloatBuffer);

		// ---------- ENTSCHEIDUNG ----------
		// pitchInHertz=mpm_pitch;
		pitch = Pitch.getInstance(PitchUnit.HERTZ, (double) pitchInHertz);
		midiKey = (int) pitch.getPitch(PitchUnit.MIDI_KEY); // PitchConverter.hertzToMidiKey((double)pitchInHertz);

		if (model.plottingSelected()) {
			plotterYIN.setData(yin.getCurrentBuffer());
			plotterYIN.setInfoString("CURRENT PITCH: " + pitchInHertz
					+ "Hz PROB: " + pitch_probability);

			plotterMPM.setData(mpm.getCurrentBuffer());
			plotterMPM.setInfoString("CURRENT PITCH: " + pitchInHertz
					+ "Hz PROB: " + pitch_probability);

			plotterBUFFER.setData(audioFloatBuffer);
			plotterBUFFER.setInfoString("level: " + level);
		}

		// nach dem pitch erkannt wurde muss ggf. entsprechend dem Instrument
		// transponiert werden
		if (model.getTransposeRecIndex() == 1) {
			// Bb Clarinet: 2 halftonesteps up
			pitch.convertPitch(2);
			pitchInHertz = (float) pitch.getPitch(PitchUnit.HERTZ);
			midiKey = PitchConverter.hertzToMidiKey((double) pitchInHertz);
		}

		String[] arr = pitch.getBaseNote(pitchInHertz).split(" ");
		String note2 = arr[0];
		int oktave = Integer.parseInt(arr[1]);
		//
		note = pitch.noteName();
		String str = "";

		// formatted Info output to GUI
		str = pitchInHertz == -1 ? "NO PITCH DETECTED" : note + " at "
				+ String.format("%.5g%n", pitchInHertz) + "Hz - IDEAL: "
				+ String.format("%.5g%n", pitch.getIdealFreq(note2, oktave))
				+ "Hz - PROB: " + String.format("%.5g%n", pitch_probability)
				+ "%";

		model.firePropertyChange(ControllerEngine.INFO_LABEL_PROPERTY, "", str);

		countMSprocessing += (audioFloatBuffer.length - overlap) / audioSampleRate * 1000.0f;

		float duration = (bufferSize - overlap) / audioSampleRate * 1000.0f;
		// finally:
//		collect(audioFloatBuffer, midiKey, duration, level, note);
	
		
		
		
		//------------------------- begin collector process -------------------------
		// ----- sammel ALLE erkannten Noten fuer statistiken -----
		if (midiKeySammlerInsgesamt.get(midiKey) != null) {
			midiKeySammlerInsgesamt.put(midiKey,
					midiKeySammlerInsgesamt.get(midiKey) + duration);
		} else {
			midiKeySammlerInsgesamt.put(midiKey, duration);
		}

		//TODO level neuer Ansatz ? zusaetzliche offset/Onset bedingung
//		levels.add(level);
//		
//		boolean minima=true;
//		Vector<double[]>extremwerte = jAMUtils.detectExtremum(levels, delta, minima);
//		
//		//sysout
//		if(!model.isEvaluating() && extremwerte.size()==1) {  //kann so immer nur 1 finden
////			jAMUtils.printArray(levels);
//			String str="";
//			for (int i = 0; i < extremwerte.size(); i++) {
//				str+= "(" + extremwerte.get(i)[0] + ","+extremwerte.get(i)[1] + "),";
//			}
//			System.out.println("============> EXTREM!!!: " + str + " at levels: " + levels);
//			System.out.println("midiKey: " + midiKey);
//			
//			levels.clear(); //wenn ein minima entdeckt: offset und dann leeren ?
//		}
		/*** 
		 * wenn eine note zb 200ms lang, dann -> 16tel aber die 50 ms muessen
		 * beim N�chsten Mal ignoriert werden wenn dies pausen entspricht !!!
		 * sonst kann sein dass zB z(3) gemalt wird obwohl z(2) !!!
		 * 
		 * if(msToIgnore>0 && !ONSET) { //MINIMUM_DURATION dur+=duration;
		 * if(dur<msToIgnore) return; else { System.out.println(
		 * "============================================>>>>>> IGNORED: " + dur
		 * + "ms OF DATA - msToIgnore: " + msToIgnore); dur=0; msToIgnore=0; } }
		 **/
		boolean silence=jAMUtils.isSilence(audioFloatBuffer, MINIMUM_LEVEL);
		// 1. OFFSET basierend auf neuer note!
		if ((ONSET && midiKey > 0  && !silence /* level > MINIMUM_LEVEL*/) && (midiKey != lastTakenMidiKey)) { // es kommt ne andere /Note/
			// System.out.println("POSSIBLE NEW NOTE: "+ midiKey);

			if (newNoteCount == 0) // die erste "andere" Note
				newNoteCount++;
			else if (midiKey == lastDetectedMidiKey)
				newNoteCount++;

			if (newNoteCount > (int) (MINIMUM_DURATION / duration)) { // 60/25.01 --> 2 also mind.  3  nehmen! wie  sonst auch
				ONSET = false; // dann macht er jetzt unten ne Entscheidung und beim next Mal f�ngt er an die neue note zu collecten
				NEW_NOTE_ONSET = true; // TODO sinn?

				newNoteCount = 0;

				//sysout
				if (!model.isEvaluating()&& jAM.SYSOUT)
					System.out.println(timestamp()+ " ================================================== OFFSET NEWNOTE: " + midiKey + " could be possible new note");
			}

			// 2. ONSET
		} else if (!ONSET && midiKey > 0 && !silence /*&& level > MINIMUM_LEVEL*/) {
			newNoteCount = 0;

			// gibts diese note schon? und kam sie beim letzten Mal?
			if (midiKeySammler.get(midiKey) != null && midiKey == lastDetectedMidiKey) {
				midiKeySammler.put(midiKey, midiKeySammler.get(midiKey) + duration);
				if (midiKeySammler.get(midiKey) > MINIMUM_DURATION) {
//					
					//sysout
					if (!model.isEvaluating()&& jAM.SYSOUT)
						System.out.println(timestamp()+ " ================================================== ONSET - Entscheidung basiert auf: "+ midiKeySammler);

					ONSET = true;

					// wir k�nnen davon ausgehen dass "note" die lastTakenNote
					// wird, da sie alle Bedingungen fuer ein note-OFFSET erf�llt
					// Problem: es kann sein dass zb 3 buffer A1 kommen, das is
					// ne new note bedingung
					// der naechst kommt dann hier rein und is aber diesmal A3
					// also lastTakenNote="A3" anstatt A1 ???
					lastTakenMidiKey = midiKey;
					
					midiKeySammler.clear();
				}
			} else {
				midiKeySammler.put(midiKey, duration);
			}

			// 3. OFFSET basierend auf Pause
		} else if (silence || midiKey == 0 /*|| level < MINIMUM_LEVEL*/) { //sonst ist alles ne Pause: kein pitch und auch level < MIN_LEVEL
			/**gibts diese Pause schon? und kam sie beim letzten Mal?
			 * ob sie beim letzten Mal kam is unwichtig
			 * es m�ssen die Pausen gez�hlt werden!
			 * BSP: 60,60,60,0,0,0,0,55,0,0,0, --> die 55 MUSS mitgezaehlt werden !
			 * ??? stimmt das?
			 * */
			if (midiKeySammler.get(0) != null && lastDetectedMidiKey==0) {
				midiKeySammler.put(0, midiKeySammler.get(0) + duration);
				
				if (midiKeySammler.get(0) > MINIMUM_DURATION) {
					if (ONSET) {
						if (!model.isEvaluating() && jAM.SYSOUT)
							System.out.println(timestamp()+ " ================================================== OFFSET - Entscheidung basiert auf: " + midiKeySammler);

						ONSET = false; // jetzt ist wieder vorbei
						NEW_NOTE_ONSET = false;
					}
					midiKeySammler.clear();
				}
			} else {
				midiKeySammler.put(0, duration);
			}
		}

		// ----- wenn nun ONSET==true anfangen zu sammeln bis ONSET==false!
		if (ONSET) {
			if (midiKeysRests.size() > 0 && !NEW_NOTE_ONSET) { // hier sind nun pausen in der off Phase gezaehlt worden
				detectRest(duration);
			}else { // sonst: sammle noten
				//hier midiKey und level speichern:
				midiKeysAndLevels.add(new Float[] { (float) midiKey, (float)level });
			}
		} else { // wenn ein OFFSET und noten sind vorhanden: entscheidung!
			if (midiKeysAndLevels.size() > 0) {
				detectNote(duration);
			}else
				// sonst sammple pausen
				midiKeysRests.add(midiKey);
		}

		lastDetectedMidiKey = midiKey;
		
		//TODO fft
//		fft(audioFloatBuffer);
		
		
		// TODO sysout
		if (!model.isEvaluating() && jAM.SYSOUT) {
			if (pitchInHertz == -1)
				System.out.println(timestamp() + "\t" + "--> Rest: "
						+ note + (note.length() == 2 ? "\t" : "")
						+ "\t\t midiKey: " + midiKey + "\t RMS: "
						+ String.format("%.2f", level) + "  Zustand: " + (ONSET? "ONSET ": "OFFSET ") + (silence ? "SILENCE" : "NO SILENCE"));
			else
				System.out.println(timestamp() + "\t" + "--> Note: "
						+ note + (note.length() == 2 ? "\t" : "")
						+ "\t midiKey: " + midiKey + "\t RMS: "
						+ String.format("%.2f", level)+ "  Zustand: " + (ONSET? "ONSET ": "OFFSET ") + (silence ? "SILENCE" : "NO SILENCE"));
		}
	}

		
//	private float test=0;
	private void detectNote(float duration) {
		//TODO optimize vorher die letzten abziehen, da diese zum naechsten gehoeren!
		
		
		//-----bleibt haengen ??? ------------------------- ??? TODO eval
//		float tmp=0;
//		while(tmp<MINIMUM_DURATION) {
//			if(midiKeysAndLevels.size()>0) {
//				midiKeysAndLevels.remove(midiKeysAndLevels.size()-1); //remove last
//				tmp += duration;
//			}
//		}
//		test=tmp;
//		tmp=0;
		//-------------------------------------------------- ???
		
		// ----- remove rests! sonst werden die evtl genommen! es soll aber eine Note ausgesucht werden!
		float noteDur = midiKeysAndLevels.size() * duration; // Laenge speichern und alle evtl gesammelten Pausen entfernen !
		
//		noteDur += test; //TODO now
		
		// --> hier dann level "innerhalb" der noten sequenz checkn!
		//returns: midiKeyTaken und duration(s) also:
		//zB: [60, 500, 500] --> anstatt 1000ms 2Mal 500ms
		//das sind bei 60bpm dann 2 8tel anstatt eine 4tel
		Vector<Double>arr = getMostDetectedNoteInSequence(midiKeysAndLevels,duration);
		int midiKeyTaken = arr.get(0).intValue();
//		int midiKeyTaken = getMostDetectedNoteInSequence(midiKeysAndLevels,duration);
		
		
		//TODO eval level minima siehe oben in collect()
		if(arr.size()==1) { //Normalfall
			addNoteOrRest(midiKeyTaken, noteDur);
		}
		else if(arr.size()>1){ //Level unterschiede in der Notensequenz!
			for (int i = 1; i < arr.size(); i++) {
				addNoteOrRest(midiKeyTaken, arr.get(i).floatValue());
			}
		}
	}

	private void detectRest(float duration) {
		float noteDur = midiKeysRests.size() * duration;

		//NE PAUSE MUSS MIND NE 16tel lang sein, sonst wird einfach ignoriert!
		int min = timeFor16thNote-MINIMUM_DURATION; //MINIMUM_DURATION; //timeFor16thNote - MINIMUM_DURATION; // -MINIMUM_DURATION;

		if (noteDur < min) {
			if(!model.isEvaluating()&& jAM.SYSOUT)
				System.out.println("NoteCollectorWorker: ########## IGNORED: " + noteDur +"ms OF RESTS!  -   min: " + min + " midiKeysRests: " + midiKeysRests );
			
			midiKeysRests.clear();
			return;
		}
		addNoteOrRest(0, noteDur);
	}
	
	private void addNoteOrRest(int midiKey, float noteDur) {
		// int noteLength = mapNoteDurationToBPM_8tel(noteDur);
		int noteLength = mapNoteDurationToBPM_16tel(noteDur);

		// ggf 2 Noten malen (mit bogen!) bei: 5 7 9 10 11 13 14 15 (bei 8tel:
		// 10 und 14)
		switch (noteLength) {
		case 5:
			convertToABC(midiKey, 4, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 1, "");
			break;
		case 7:
			convertToABC(midiKey, 6, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 1, "");
			break;
		case 9:
			convertToABC(midiKey, 8, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 1, "");
			break;
		case 10:
			convertToABC(midiKey, 8, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 2, "");
			break;
		case 11:
			convertToABC(midiKey, 8, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 3, "");
			break;
		case 13:
			convertToABC(midiKey, 12, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 1, "");
			break;
		case 14:
			convertToABC(midiKey, 12, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 2, "");
			break;
		case 15:
			convertToABC(midiKey, 12, midiKey == 0 ? "" : "-");
			convertToABC(midiKey, 3, "");
			break;

		default:
			convertToABC(midiKey, noteLength, "");
			break;
		}

		if (!model.isEvaluating())
			model.updateScore(notesAsString);

		if (midiKey == 0) {
			if (!model.isEvaluating()&& jAM.SYSOUT)
				// jAM.log("NoteCollectorWorker: ==> ENTSCHEIDUNG REST: ("+noteLength+") noteDur: "+noteDur+" - based on: "
				// + midiKeysRests, false);
				System.err.println(timestamp() + " ==> ENTSCHEIDUNG REST: ("+ noteLength + ") noteDur: " + noteDur + " - based on 'RESTS':" + midiKeysRests);
		} else {
			if (!model.isEvaluating()&& jAM.SYSOUT) {
				// jAM.log("NoteCollectorWorker: ==> ENTSCHEIDUNG NOTE: "+midiKey+"("+noteLength+") noteDur: "+noteDur+" - based on: "
				// + midiKeysNotes, false);
				String s="";
				for (int i = 0; i < midiKeysAndLevels.size(); i++) {
					s+=(midiKeysAndLevels.get(i)[0]+",");
				}s+="\n";
				System.err.print(timestamp() + " ==> ENTSCHEIDUNG NOTE: " + midiKey + "(" + noteLength + ") noteDur: " + noteDur + " - based on: " + s);
			}
		}
		//immer beide loeschen, sonst werden features gesammelt, welche schon vor langer zeit auftraten
		midiKeysAndLevels.clear();
		midiKeysRests.clear();
		
		// wir brauchen midiKey und Notenwert(zwischen 1-16)
		evaluationSammler.add(midiKey);
		evaluationSammler.add(noteLength);
	}

	/** 
	 * This function converts time in ms to a notevalue, based on beats per minute.<br />
	 * <br />
	 * Example: 60 bpm, so a 16th notevalue is equivalent to 250ms (in perfect case), 8th==500ms 4th==1000 ms ...<br />
	 * the tolerance is 125ms, so we just take exactly the middle:<br />
	 * <pre>
	 * ...........|...........|...........|...........|........
	 * ....16th...|....8th....|dotted 8th.|...4th.....|........
	 * -----|-----|-----|-----|-----|-----|-----|-----|-------- and so on
	 * ----250---375---500---625---750---875---1000---1125----> detected notelength in ms
	 * </pre>
	 * */
	private int mapNoteDurationToBPM_16tel(float duration) {
		int notenwert = -1;
		int tolerance = timeFor16thNote / 2;

		// TODO: mathematische Funktion dafuer:
		// 0-200 : 1
		// 200-400: 2
		// usw... ???

		if (duration >= 0 && duration < timeFor16thNote + tolerance) { // bis 16tel
//			msToIgnore = timeFor16thNote - duration;
			notenwert = 1; // alles "unter" ner 16tel muss dann ne 16tel werden
		} else if (duration >= 2 + tolerance && duration < timeFor16thNote * 2 + tolerance) {
//			msToIgnore = timeFor16thNote * 2 - duration;
			notenwert = 2;
		} else if (duration >= timeFor16thNote * 2 + tolerance && duration < timeFor16thNote * 3 + tolerance) {
//			msToIgnore = timeFor16thNote * 3 - duration;
			notenwert = 3;
		} else if (duration >= timeFor16thNote * 3 + tolerance && duration < timeFor16thNote * 4 + tolerance) {
//			msToIgnore = timeFor16thNote * 4 - duration;
			notenwert = 4;
		} else if (duration >= timeFor16thNote * 4 + tolerance && duration < timeFor16thNote * 5 + tolerance) {
//			msToIgnore = timeFor16thNote * 5 - duration;
			notenwert = 5;
		} else if (duration >= timeFor16thNote * 5 + tolerance && duration < timeFor16thNote * 6 + tolerance) {
//			msToIgnore = timeFor16thNote * 6 - duration;
			notenwert = 6;
		} else if (duration >= timeFor16thNote * 6 + tolerance && duration < timeFor16thNote * 7 + tolerance) {
//			msToIgnore = timeFor16thNote * 7 - duration;
			notenwert = 7;
		} else if (duration >= timeFor16thNote * 7 + tolerance && duration < timeFor16thNote * 8 + tolerance) {
//			msToIgnore = timeFor16thNote * 8 - duration;
			notenwert = 8;
		} else if (duration >= timeFor16thNote * 8 + tolerance && duration < timeFor16thNote * 9 + tolerance) {
//			msToIgnore = timeFor16thNote * 9 - duration;
			notenwert = 9;
		} else if (duration >= timeFor16thNote * 9 + tolerance && duration < timeFor16thNote * 10 + tolerance) {
//			msToIgnore = timeFor16thNote * 10 - duration;
			notenwert = 10;
		} else if (duration >= timeFor16thNote * 10 + tolerance && duration < timeFor16thNote * 11 + tolerance) {
//			msToIgnore = timeFor16thNote * 11 - duration;
			notenwert = 11;
		} else if (duration >= timeFor16thNote * 11 + tolerance && duration < timeFor16thNote * 12 + tolerance) {
//			msToIgnore = timeFor16thNote * 12 - duration;
			notenwert = 12;
		} else if (duration >= timeFor16thNote * 12 + tolerance && duration < timeFor16thNote * 13 + tolerance) {
//			msToIgnore = timeFor16thNote * 13 - duration;
			notenwert = 13;
		} else if (duration >= timeFor16thNote * 13 + tolerance && duration < timeFor16thNote * 14 + tolerance) {
//			msToIgnore = timeFor16thNote * 14 - duration;
			notenwert = 14;
		} else if (duration >= timeFor16thNote * 14 + tolerance && duration < timeFor16thNote * 15 + tolerance) {
//			msToIgnore = timeFor16thNote * 15 - duration;
			notenwert = 15;
		} else if (duration >= timeFor16thNote * 15 + tolerance && duration < timeFor16thNote * 16 + tolerance) {
//			msToIgnore = timeFor16thNote * 16 - duration;
			notenwert = 16;
		} else if (duration >= timeFor16thNote * 16 + tolerance) {
			// msToIgnore = ???;
			notenwert = 1;
		}

		// TODO mapping auch drueber hinaus !
		// wenn zB 4 takte und eine 16tel dann muss dies auch so gemalt werden
		// und nich einfach ne 16tel !
		return notenwert;
	}

	private Vector<Double> getMostDetectedNoteInSequence(Vector<Float[]> midiKeysAndLevels, float durationOfOneNote) {		
		int midiKey = 0;
		int cnt=0;
		Vector<Double> ret = new Vector<Double>();
		
		float[] midiKeys = new float[midiKeysAndLevels.size()];
		double [] levels = new double[midiKeysAndLevels.size()];
		
		// 1. erst z�hlen:
        int[] modeArray = new int[128]; //128 midiKeys !
        
        //copy from Vector<Float[]> to float[]
        for (int i = 0; i < midiKeys.length; i++) {
        	midiKeys[i] = midiKeysAndLevels.get(i)[0];
        	levels[i] = Math.abs(midiKeysAndLevels.get(i)[1]); //betrag, dann nach maxima suchen
		}
        
        for(int i = 0; i < midiKeys.length; i++) {
            modeArray[(int)midiKeys[i]] += 1; //count
        }
        
        //2. dann den Haeufigsten suchen
        for(int i = 0; i < modeArray.length; i++){
        	if(modeArray[i]>cnt) {
        		cnt = modeArray[i];
        		midiKey=i;
        	}
        }
//      System.out.println(midiKey + " occures " + cnt + " times");
        
//      return midiKey;
        
        //first add taken MidiKey
        ret.add((double)midiKey);
        
		/**minima/maxima: idea:
		 * if there are local minima in the levelVector of this notesequence
		 * then we can assume that the SAME note was played more than one time!
		 * */
		//find extrema in RMS Levels
		boolean minima=false;
		Vector<double[]>extremwerte = jAMUtils.detectExtremum(levels, delta, minima);
		//sysout
		if(!model.isEvaluating() && extremwerte.size()>0) {
			System.out.print("EXTREMWERTE " + (minima ? " MINIMA ":" MAXIMA ") +" ===> [");
			for (int i = 0; i < extremwerte.size(); i++) {
				System.out.print("(" + extremwerte.get(i)[0] + "," + extremwerte.get(i)[1] + "), ");
			}
			System.out.println("] durationOfOneNote: " + durationOfOneNote);
		}
		
		//midiKey, notevalue1, notevalue2 usw...
//		return i.e. [60, 512, 511..]
		
		//then add possible durations
		if(extremwerte.size()>0) {
			double startX=0;
			double endX=0;
			
			for (int i = 0; i < extremwerte.size(); i++) {
				endX=extremwerte.get(i)[0]; //nich beim Tiefpunkt sondern ungefaehr beim Naechsten ONSET!
				
//				ret.add( durationOfOneNote * (endX-startX) );
				
//				//ignoriere extremwerte am Ende, denn dies kommt sehr oft vor! also nur die ersten X%
				float X = (levels.length*75.0f/100);
				if(endX < X) {
					ret.add( durationOfOneNote * (endX-startX) );
				}
				
				startX=endX; //-2;
			}
			
			//nur wenn mind. eine duration geaddet wurde noch zum Ende:
			if(ret.size()>1) {
				endX = levels.length;
				ret.add( durationOfOneNote * (endX-startX) );
			}
		}
		return ret;
	}
	
	

	public void setPITCHDETECTOR(String PITCHDETECTOR) {
		this.PITCHDETECTOR = PITCHDETECTOR;
	}

	private float getBestPitch(float[] audioFloatBuffer) {
		if (PITCHDETECTOR.equals("YIN")) {
			float yin_pitch = yin.getPitch(audioFloatBuffer);
			// Pitch pitchYIN=
			// Pitch.getInstance(PitchUnit.HERTZ,(double)yin_pitch);
			// int midiKeyYIN = (int)pitchYIN.getPitch(PitchUnit.MIDI_KEY);
			// float yin_prob = yin.getProbability();
			yin_cnt++;
			pitch_probability = yin.getProbability();
			return yin_pitch;
		} else if (PITCHDETECTOR.equals("MPM")) {
			float mpm_pitch = mpm.getPitch(audioFloatBuffer);
			// Pitch pitchMPM=
			// Pitch.getInstance(PitchUnit.HERTZ,(double)mpm_pitch);
			// int midiKeyMPM = (int)pitchMPM.getPitch(PitchUnit.MIDI_KEY);
			// float mcleod_prob = mpm.getProbability();
			pitch_probability = mpm.getProbability();
			mpm_cnt++;
			return mpm_pitch;
		} else {
			System.err.println("SHIT: PITCHDETECTOR: " + PITCHDETECTOR);
			System.exit(-1);
			return -1;
		}

		// TODO "2 Ohren " ????? erstmal beide evaluieren

		// if(midiKeyYIN==midiKeyMPM) { // --> wird am H�ufigsten eintreten !
		// yin_cnt++;
		// pitch_probability=yin_prob;
		// return yin_pitch;
		// }
		// else {
		// if(midiKeyYIN==lastDetectedMidiKey) {//dann nehme YIN pitch
		// yin_cnt++;
		// pitch_probability=yin_prob;
		// return yin_pitch;
		// }
		// else {
		// mpm_cnt++;
		// pitch_probability=mcleod_prob;
		// return mpm_pitch;
		// }
		// }
	}

	

	private String letzteNote = "";

	public void convertToABC(int midiKey, int notenWert, String bindeBogenSameNotes) {
		String NOTE = "";

		// wenn zB C10 erkannt oder irgendwas ausserhalb meines bereichs ->
		// Pause!
		if (midiKey < 36 || midiKey > 96)// von C2 bis C7
			NOTE = "z";
		else {
			// 1. Wenn midiKey in Tonleiter: map to right note (b ein
			// hoch(Bb->B), # ein runter(F#->F))
			boolean inTonleiter = jAMUtils.inTonleiter(midiKey, tonleiter);

//			System.out.println("====>" + midiKey + FLAT_KEY + " - "+ inTonleiter);

			if (FLAT_KEY && inTonleiter && jAMUtils.ABC_NOTES_FLAT[midiKey].contains("_"))
				midiKey += 1;

			else if (!FLAT_KEY && inTonleiter && jAMUtils.ABC_NOTES_SHARP[midiKey].contains("^"))
				midiKey -= 1;

//			System.out.println("====>" + ABC_NOTES_FLAT[midiKey].contains("_"));
//			System.out.println("====>" + midiKey);

			// wenn nich in Tonleiter wird einfach sharp gemalt!
			NOTE = jAMUtils.ABC_NOTES_SHARP[midiKey];

			// old:
			// NOTE = FLAT_KEY ? ABC_NOTES_FLAT[midiKey] :
			// ABC_NOTES_SHARP[midiKey];
		}

		String PREFIX = ""; // zB "(" oder auch ")" dann: ")A4" kann evtl sein!
		String POSTFIX = ""; // zB ")" oder "|" oder "\n"

		lenge += notenWert;

		POSTFIX += bindeBogenSameNotes; // is entweder "" oder "-"

		// TODO stimmt das alles?
		if (lenge >= OBEN * 4 && takte < UNTEN) {
			// System.out.println("=========================================== EIN TAKT VORBEI");
			POSTFIX += "|";
			lenge = 0;
			takte++;

			model.firePropertyChange(ControllerEngine.SCROLL_DOWN_PROPERTY, -1, 100);

		} else if (lenge >= OBEN * 4 && takte == UNTEN) {
			// System.out.println("=========================================== 4 TAKTE VORBEI ===========================================");
			POSTFIX += "\n";
			takte = 1;
			lenge = 0;
		}

		/*
		 * imSelbenTakt(letzte UND! NOTE) && TODO ob im selben Takt wird
		 * noch ignoriert!
		 */
	// wenn letzte und diese unabgh�ngig von #/b DIESELBE IST:
		if ( (letzteNote.replace("_", "").equals(NOTE.replace("_", "")) || letzteNote
				.replace("^", "").equals(NOTE.replace("^", ""))) &&
				// wenn letzte nun b oder # hatte und DIESE NICHT: NATURAL SIGN!
				(letzteNote.contains("^") && !NOTE.contains("^"))
				|| (letzteNote.contains("_") && !NOTE.contains("_")))
			PREFIX = "="; // natural sign

		notesAsString += (PREFIX + NOTE + notenWert + POSTFIX);
		letzteNote = NOTE;
	}
}