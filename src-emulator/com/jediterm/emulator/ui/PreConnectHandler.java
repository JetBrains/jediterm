package com.jediterm.emulator.ui;

import com.jediterm.emulator.Questioner;
import com.jediterm.emulator.display.BufferedTerminalWriter;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class PreConnectHandler implements Questioner, KeyListener {
	private Object sync = new Object();
	private BufferedTerminalWriter tw;
	private StringBuffer answer;
	private boolean visible;
	
	public PreConnectHandler(BufferedTerminalWriter tw){
		this.tw = tw;
		this.visible = true;
	}
	
	// These methods will suspend the current thread and wait for 
	// the event handling thread to provide the answer.
	public String questionHidden(String question){
		visible = false;
		String answer = questionVisible(question, null);
		visible = true;
		return answer;
	}

	public String questionVisible(String question, String defValue){
		synchronized (sync) {
			tw.writeUnwrappedString(question);
			answer = new StringBuffer();
			if(defValue != null){ 
				answer.append(defValue);
				tw.writeUnwrappedString(defValue);
			}
			try {
				sync.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			String answerStr = answer.toString();
			answer = null;
			return answerStr;
		}
	}
	
	public void showMessage(String message){
		tw.writeUnwrappedString(message);
		tw.nextLine();
	} 

	public void keyPressed(KeyEvent e){
		if(answer == null) return;
		synchronized(sync){
			boolean release = false;
			
			switch(e.getKeyCode()){
			case KeyEvent.VK_BACK_SPACE:
				if(answer.length() > 0){
					tw.backspace();
					tw.eraseInLine(0);
					answer.deleteCharAt( answer.length() - 1 );
				}
				break;
			case KeyEvent.VK_ENTER:
				tw.nextLine();
				release = true;
				break;
			}
			
			if(release) sync.notifyAll();
		}
			
	}

	public void keyReleased(KeyEvent e){
		
	}

	public void keyTyped(KeyEvent e){
		if(answer == null) return;
		char c = e.getKeyChar();
		if (  Character.getType(c) != Character.CONTROL ){
			if(visible) tw.writeString( Character.toString(c) );
			answer.append(c);
		}
	}

	
	
}
