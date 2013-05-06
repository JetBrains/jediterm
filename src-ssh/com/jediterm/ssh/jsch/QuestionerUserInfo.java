/**
 * 
 */
package com.jediterm.ssh.jsch;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.jediterm.emulator.Questioner;

class QuestionerUserInfo implements UserInfo, UIKeyboardInteractive{
	private Questioner questioner;
	private String password;
	private String passPhrase;
	
	public QuestionerUserInfo(Questioner questioner){
		this.questioner = questioner;
	}
	
	public String getPassphrase(){
		return passPhrase;
	}

	public String getPassword(){
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}

	public boolean promptPassphrase(String message){
		passPhrase = questioner.questionHidden(message + ":");
		return true;
	}

	public boolean promptPassword(String message){
		password = questioner.questionHidden(message + ":");
		return true;
	}

	public boolean promptYesNo(String message){
		String yn = questioner.questionVisible(message + " [Y/N]:" , "Y");
		String lyn = yn.toLowerCase();
		if( lyn.equals("y") || lyn.equals("yes") ){
			return true;
		}else{
			return false;
		}
	}

	public void showMessage(String message){
		questioner.showMessage(message);
	}

	public String[] promptKeyboardInteractive(final String destination, final String name,
			final String instruction, final String[] prompt, final boolean[] echo){
		int len = prompt.length;
		String [] results = new String[len];
		if(destination != null && destination.length() > 0 ) questioner.showMessage(destination);
		if(name != null && name.length() > 0 ) questioner.showMessage(name);
		questioner.showMessage(instruction);
		for(int i = 0; i < len ; i++ ){
			String promptStr = prompt[i];
			results[i] = echo[i] ? questioner.questionVisible(promptStr, null) :
								   questioner.questionHidden(promptStr) ;
		}
		return results;
	}

}