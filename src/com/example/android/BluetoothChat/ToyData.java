/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.android.BluetoothChat;

/**
 *
 * @author Administrator
 */
public class ToyData {
    private static final byte mhead1st= (byte) 0xF0;
    private static final byte mhead2nd=(byte) 0xF1;
    private static final byte mtail1st= (byte) 0x0D;
    private static final byte mtail2snd = (byte) 0x0A;
    private byte mchannel; //range over 0x01-0x04
    private byte mangle; //range over 0-30 degree
    private byte mspeed; // range over  1 degree/second
    private byte mparity; // sum of bits of channel, angle, speed,
    private byte madditional=0;
    private static int byteNum = 9;
    
    public void setChannel(byte channel) {
        mchannel = channel;
    }
    
    public void setAngle(byte angle) {
        mangle = angle;
    }
    
    public void setSpeed(byte speed) {
        mspeed = speed;
    }
    
        public void setChannel(int channel) {
        mchannel = (byte) channel;
    }
    
    public void setAngle(int angle) {
        mangle = (byte) angle;
    }
    
    public void setSpeed(int speed) {
        mspeed = (byte) speed;
    }
    
    private void setParity() {
        mparity = (byte)(mchannel +  mangle + mspeed); 
    }
    
    
    public byte[] getData (){
        byte [] mdata = new byte[byteNum];
        setParity();
        final int i = 0;
        mdata[0] = mhead1st;
        mdata[1] = mhead2nd;
        mdata[2] = mchannel;
        mdata[3] = mangle;
        mdata[4] = mspeed;        
        mdata[5] = mparity;
        mdata[6] = madditional;
        mdata[7] = mtail1st;
        mdata[8] = mtail2snd;
        return mdata;
        
    }

    
}
