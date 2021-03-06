package de.hsa.jam.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;
import java.util.logging.Logger;

import scanner.PositionableInCharStream;

import abc.notation.MusicElement;
import abc.notation.NoteAbstract;
import abc.notation.SlurDefinition;
import abc.notation.Tune;
import abc.ui.swing.JScoreComponent;
import abc.ui.swing.JScoreElement;
import abc.ui.swing.ScoreTemplate;

/**
 * This class implements a ScorePanel.<br /> 
 * Based on abcnotation and abc4j it draws the detected notes.
 *
 * @author Michael Wager
 */
public class ScorePanel extends JScoreComponent {
	private Logger LOG = Logger.getLogger(ScorePanel.class.getName());

	public ScorePanel() {
		//bringt nichts... TODO drag n drop ?
		this.addMouseListener( new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {

				JScoreElement sel = ScorePanel.this.getScoreElementAt(e.getPoint());
				if (sel!=null)
					System.out.println("Score element at " + e.getX() + " / " + e.getY() + " : " + sel + "@" + sel.getBase());
				//SRenderer sel = getScoreElementAt(e.getPoint());
				
				setSelectedItem(sel);
				
//				if (sel!=null) {
//					MusicElement elmnt = sel.getMusicElement();
////					m_tuneBookEditorPanel.getTuneEditSplitPane().getScore().setSelectedItem(sel);
//					if (elmnt!=null && elmnt instanceof PositionableInCharStream){
////						m_tuneBookEditorPanel.getTuneEditArea().setSelectedItem((PositionableInCharStream)elmnt);
//		    			// Dumping element properties
//						if (elmnt instanceof NoteAbstract) {
//							NoteAbstract note = (NoteAbstract)elmnt;
//							System.out.println("properties for " + elmnt + " : slur?="+ note.isPartOfSlur() + " isLastOfGroup?=");
//							String test = "";
//							Vector slurs = note.getSlurDefinitions();
//							int size = slurs.size();
//							test = size==0?"no slur":(size+" slur"+(size>1?"s":""));
//							int i = 0;
//							while (i < size) {
//								SlurDefinition slur = (SlurDefinition) slurs.elementAt(i);
//								test += "start:"+slur.getStart()+" end:"+slur.getEnd();
//								if (size > 1)
//									test += " | ";
//								i++;
//							}
//							System.out.println(test);
//						}
//		    		}
//				}
			}
		});
		
		// mein frequenzbereich:
		// E,,F,,G,,A,,|B,,C,D,E,|F,G,A,B,|CDEF|GABc|defg|abc'd'|e'f'g'a'|b'c''d''e''
		// String tuneAsString =
		// "X:0\nT:Toller Noteneditor\nT:untertitel\nM:4/4\nK:C\nL:1/4\nE,,F,,G,,A,,|B,,C,D,E,|F,G,A,B,|CDEF|GABc|defg|abc'd'|e'f'g'a'|b'c''d''e''";
		/**
		 * AMAZING GRACE X:0 T:Amazing Grace (BbClari) source Q:1/4 = 90 M:3/4
		 * L:1/16 K:F C2F2|"F"F8(3A2G2F2|"F"A8A2G2|"Bb"F8D4|"F"C8C2F2|
		 * "F"F8(3A2G2F2|"F"A8G,2A,2|("C"C12|C8)A,2C2|
		 * "F"C8(3A,2G,2F,2|"F"A,8A2G2|"Bb"F8D4|"F"C8C2F2|
		 * "F"F8(3A2G2F2|"C"A8G4|("F"F12|F8)z4|
		 * 
		 * 
		 * X:0 T:Kalinka Q:1/4 = 300 M:4/4 L:1/16 K:F
		 * A8|:G8E4F4|G8E4F4|G8F4E4|D8A4A4|G6F2E4F4|G8F4E4|
		 * G8F4E4|D8A8:|D8(d8|c8B8)||(A4c4)B4A2G2|F8C8|(A4c4)B4A2G2|
		 * F8C8|D8D4E4|(G4F4)E4D4|C8C8|(C4c12)|A4c4G4A4|F8C8|
		 * A4c4G4A4|F8C8|D8D4E4|G4F4(E4D4)|c8B8|A8A8||
		 * 
		 * 
		 * %%% mein unterst�tzter FreqBereich TODO f�r doc Kapitel:
		 * "Die Abtastrate" X:1 T:Frequenzbereich K:C
		 * z4|z4|z4|CDEF|GABc|def|gabc'|d'e'f'g'|a'b'c'' K:C bass
		 * C,,D,,E,,F,,|G,,A,,B,,C,|D,E,F,G,|A,B,z2|
		 **/
		// feidman klezmer:
		// tuneAsString =
		// "%%% TUTORIAL: http://www.lesession.co.uk/abc/abc_notation.htm#rests\n";
		// tuneAsString += "X:0" + NEWLINE;
		// tuneAsString += "T:Bless you\nT:klezmer\nC:giora feidman" + NEWLINE;
		// // tuneAsString += "source" + NEWLINE;
		// tuneAsString += "Q:1/4 = 100" + NEWLINE;
		// tuneAsString += "M:4/4" + NEWLINE;
		// tuneAsString += "L:1/16" + NEWLINE;
		// tuneAsString += "K:C" + NEWLINE;
		//
		// tuneAsString +=
		// "\"am\"A,3E,A,zC2E2z4E2|D3FEzD2A,2z4z2|C2C2A,2C2D2D2C2A,2|C2z4EEE2DCC2B,A,"
		// + NEWLINE;
		// tuneAsString +=
		// "A,3E,A,zC2E2z4E2|(G2F2)(E2D2)A,2z4z2|C2C2A,2C2D2D2C2B,2|A,4z8z4" +
		// NEWLINE;
		// tuneAsString +=
		// "|:\"am\"E2z2A2z2E2z2A2z2|\"dm\"G2F2E2F2D4z4|\"dm\"F2F2D2F2A2A2G2F2|\"am\"E8z8|\"am\"E2E2C2E2G2G2F2E2|\"dm\"D2z2A4F4D4|\"am\"C2D2E2C2\"dm\"D2D2C2D2|\"am\"E8z8:|\"am\"C2D2E2C2\"dm\"D2D2C2B,2|\"am\"A,8z8|"
		// + NEWLINE;
		// tuneAsString += "(3bbb(3bbb(abcd)(ABCG)" + NEWLINE;
		// tuneAsString +=
		// "\"FEHLERHAFT!\"z8z4z2z1|z16|CDEFGAB|cdefgab|c'd'e'f'g'a'b'" +
		// NEWLINE;
		// tuneAsString += "K:C bass" + NEWLINE;
		// tuneAsString +=
		// "\"FEHLERHAFT!\"E,,F,,G,,A,,B,,|C,D,E,F,G,A,B,|z8z4z2z1|z16|" +
		// NEWLINE;

		// parseAndSetTune(tuneAsString, true);
	}

	public void updateScore(Tune tune) {
		setTune(tune);
	}

	public void selectNote(MusicElement e) {
		this.setSelectedItem(e);
	}

	// public String transpose(int semitones) {
	// tune = Tune.transpose(tune, semitones); //transpose !!! TODO
	// setTune(tune);
	//
	// // tuneAsString = new TuneParser().parseTune(tune);
	// return tune.getTuneAsString();
	// }
	//
	//
}