/*
 Copyright [2012] [AT&T]
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
       http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.att.aro.pcap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to check if a pcap-ng file is created by tcpdump tool on Mac OS Maericks
 *
 */
public class PcapngHelper {
	private String prevfilepath;
	private long prevlastmodifytime = 0;
	private String hardware =  "";
	String os = "";
	String appname = "";
	int osVersion = 0;
	int osMajor = 0;
	int appVersion = 0;
	boolean result = false;
	/**
	 * check pcapng file header to see if it is created by Apple Tool
	 * @return
	 * @throws IOException 
	 */
	public boolean isApplePcapng(File file) throws IOException{
		//reuse previous result if the same file is passed in for calculation
		if(file.lastModified() == this.prevlastmodifytime && 
				file.getAbsolutePath().equals(this.prevfilepath)){
			return result;
		}
		
		this.prevfilepath = file.getAbsolutePath();
		this.prevlastmodifytime = file.lastModified();
		
		FileInputStream stream = new FileInputStream(file);
		int size = 2048;//header size should never be bigger than this.
		if(file.length() < size){
			size = (int)file.length();
		}
		byte[] data = new byte[size];
		stream.read(data);
		stream.close();
		ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
		int blocktype = 0x0A0D0D0A;
		int type = buffer.getInt();
		if(type != blocktype){
			return result;
		}
		int blocklen = buffer.getInt();
		int startpos = 24;
		short option_code = 0;
		short option_len = 0;
		byte[] dst = null;
		int mod;
		int stop = blocklen - 4;
		buffer.position(startpos);
		do{
			option_code = buffer.getShort();
			option_len = buffer.getShort();
			startpos = buffer.position();

			if(option_len > 0){
				dst = new byte[option_len];
				buffer.get(dst, 0, option_len);
				startpos = buffer.position();
			}
			switch(option_code){
			case 2://hardware like x86_64 etc.
				hardware = new String(dst);
				break;
			case 3://Operating System like Mac OS 10.8.5
				os = new String(dst);
				break;
			case 4://name of application that created this packet file like tcpdump( libpcap version 1.3)
				appname = new String(dst);
				break;
			}
			//16 bit align and 32 bit align
			mod = startpos % 2;
			startpos += mod;
			mod = startpos % 4;
			startpos += mod;
			buffer.position(startpos);
		}while(option_code > 0 && option_len > 0 && startpos < stop);
		if(os.length() > 1 && appname.length() > 1){
			/* look for
			 * OS >= Darwin 13.0.0 
			 * App: tcpdump (libpcap version 1.3.0 - Apple version 41) 
			 */
			extractOSVersion();
			extractAppVersion();
			if(osVersion >= 13 && osMajor >= 0 && appVersion >= 41){
				result = true;
			}
		}
		return result;
	}
	void extractOSVersion(){
		Pattern pattern = Pattern.compile("Darwin (\\d+)\\.(\\d+)");
		Matcher match = pattern.matcher(os);
		boolean success = match.find();
		if(success){
			osVersion = Integer.parseInt(match.group(1));
			osMajor = Integer.parseInt(match.group(2));
		}
	}
	void extractAppVersion(){
		Pattern pattern = Pattern.compile("tcpdump.+Apple version (\\d+)");
		Matcher match = pattern.matcher(appname);
		boolean success = match.find();
		if(success){
			appVersion = Integer.parseInt(match.group(1));
		}
	}
	public String getHardware() {
		return hardware;
	}
	public String getOs(){
		return os;
	}
	public String getAppname(){
		return appname;
	}
}
