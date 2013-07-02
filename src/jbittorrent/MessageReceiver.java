/*
 * jbittorrent library is an implementation in Java language of BiTorrent protocol.
 *
 * It is based on the Java Bittorrent API of Baptiste Dubuis, Artificial Inteligency Laboratory, EPFL.
 * @version 1.0
 * @author Baptiste Dubuis
 * To contact the author:
 * email: baptiste.dubuis@gmail.com
 *
 * More information about Java Bittorrent API:
 * http://sourceforge.net/projects/bitext/
 *
 * New contribution are:
 * 1. Optimization of process establishement of conecctions betwen the peers of the swarm.
 * 2. Improvements in the Choking Algorithm.
 * 3. Improvements in the Optimistic Unchoking implementation.
 * 4. Implementation of Rarest First algorithm .
 * 5. Implementation of End Game Strategy.
 *
 * This project contains three packs:
 * 1. jbittorrent is the "client" part, i.e. it implements all classes needed to publish files, share them and download them.
 * 2. trackerBT is the "tracker" part, i.e. it implements all classes needed to run a Bittorrent tracker that coordinates peers exchanges.
 * 3. test contains example classes on how a developer could create new applications and new .torrent file.
 *
 * Copyright (C) 2013 Sandra Ferrer, AST Research Group
 *
 * jbittorrent is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published 
 * by the Free Software Foundation; either version 2 of the License, 
 * or (at your option) any later version.
 *
 * jbittorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  @version 1.0
 *  @author Sandra Ferrer Celma <sandra.ferrer@urv.cat>
 * 
 */

package jbittorrent;

import java.io.*;
import java.util.LinkedList;

import javax.swing.event.EventListenerList;

import org.apache.log4j.Logger;

/**
 * Thread created to listen for incoming message from remote peers. When data is read,
 * message type is determined and a new Message object (either Message_HS or Message_PP)
 * is created and passed to the corresponding receiver
 * 
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class MessageReceiver extends Thread {

	private static Logger logger = Logger.getLogger(MessageReceiver.class);
	
    private boolean run = true;
    private InputStream is = null;
    private DataInputStream dis = null;
    private boolean hsOK = false;
    private final EventListenerList listeners = new EventListenerList();
    
    private LinkedList<Message_PP> refreshEndGameStrategy;
    private LinkedList<Message_PP> cancelPieceEndGameStrategy;

    /**
     * Create a new Message receiver for a given peer
     * @param id The id of the peer that has been assigned this receiver
     * @param is InputStream
     * @throws IOException
     */
    public MessageReceiver(String id, InputStream is) throws IOException {
        //this.setName("MR_" + id);
        this.is = is;
        this.dis = new DataInputStream(is);
        this.refreshEndGameStrategy = new LinkedList<Message_PP>();
        this.cancelPieceEndGameStrategy = new LinkedList<Message_PP>();
    }

    /**
     * Reads bytes from the DataInputStream
     * @param data byte[]
     * @return int
     *
     */
    private int read(byte[] data){
        try{
            this.dis.readFully(data);
        }catch(IOException ioe){
            return -1;
        }
        return data.length;
    }

    /**
     * Reads bytes from theInputStream
     * @param data byte[]
     * @return int
     * @throws IOException
     * @throws InterruptedException
     * @deprecated
     */
	private int read2(byte[] data)throws IOException, InterruptedException{
        int totalread = 0;
        int read = 0;
        while(totalread != data.length){
            if((read = this.is.read(data, totalread, data.length - totalread)) == -1)
                return -1;
            totalread += read;
            Thread.sleep(50);
        }
        return totalread;
    }

    /**
     * Reads bytes from the input stream. This read method read exactly the number of
     * bytes corresponding to the length of the byte array given in parameter
     * @param data byte[]
     * @return int
     * @throws IOException
     * @throws InterruptedException
     * @throws Exception
     * @todo Optimize this method which seems to take too much time...
     */
    private int read1(byte[] data) throws IOException, InterruptedException, Exception {
        int l = data.length;
        byte[] payload = new byte[0];
        int loop = 0;
        for (int i = 0; i < l; ) {
            loop++;
            int available = is.available();

            if (available < l - i) {
                loop++;
                byte[] temp = new byte[available];
                if (is.read(temp) == -1){
                    return -1;
                }
                payload = Utils.concat(payload, temp);
                i += available;
                Thread.sleep(10);
            } else {
                byte[] temp = new byte[l - i];
                if (is.read(temp) == -1){
                    return -1;
                }
                payload = Utils.concat(payload, temp);
                Utils.copy(payload, data);
                return payload.length;
            }
        }
        return -1;
    }
    
    /**
     * Introduces a new message: REFRESH_ENDGAME_STRATEGY.
     * <p>.  It is an internal message that doesn’t belong to the bittorrent protocol.  
     * The task is informed that the selected piece is already downloaded by another task.   
     * Consequently, has to restart the process of selection of piece.
     */
    public void pushMessageRefreshEndGameStrategy(){
    	Message_PP mess = new Message_PP();
    	mess.setData(PeerProtocol.REFRESH_ENDGAME_STRATEGY);
    	this.refreshEndGameStrategy.add(mess);
    }
    
    /**
     * Introduces a new message: CAMCEL_PIECE_ENDGAME_STRATEGY message.
     * <p> It is an internal message that doesn’t belong to the bittorrent protocol.  
     * The task is informed that the selected piece is already downloaded by another task.  
     * Will have to send the cancelation messages, of those blocks that have been solicited 
     * and are waiting receive. 
     * @param indexPiece int Index of piece
     */
    public void pushMessageCancelPieceEndGameStrategy(int indexPiece){
    	Message_PP mess = new Message_PP();
    	mess.setData(PeerProtocol.CAMCEL_PIECE_ENDGAME_STRATEGY, Utils.intToByteArray(indexPiece));
    	this.cancelPieceEndGameStrategy.add(mess);
    }
    

    /**
     * Reads data from the inputstream, creates new messages according to the
     * received data and fires MessageReceived method of the listeners with the
     * new message in parameter. Loops as long as the 'run' variable is true
     */
    public void run() {
        //Message m = null;
        int read = 0;
        byte[] lengthHS = new byte[1];
        byte[] protocol = new byte[19];
        byte[] reserved = new byte[8];
        byte[] fileID = new byte[20];
        byte[] peerID = new byte[20];
        byte[] length = new byte[4];
        Message_HS hs = new Message_HS();
        Message_PP mess = new Message_PP();

        while (this.run) {
            int l = 1664;
            try {
                if (!hsOK) {
                    //System.out.println("Wait for hs");

                    if ((read = this.read(lengthHS)) > 0) {
                        for (int i = 0; i < 19; i++)
                            protocol[i] = (byte) is.read();
                        for (int i = 0; i < 8; i++)
                            reserved[i] = (byte) is.read();
                        for (int i = 0; i < 20; i++)
                            fileID[i] = (byte) is.read();
                        for (int i = 0; i < 20; i++)
                            peerID[i] = (byte) is.read();

                        hs.setData(lengthHS, protocol, reserved,
                                           fileID, peerID);
                        //this.hsOK = true;
                    } else {
                        hs = null;
                    }
                } else {
                	
                	if(!this.refreshEndGameStrategy.isEmpty()){
                		for(Message_PP m: this.refreshEndGameStrategy){
                			this.fireMessageReceived(m);
                		}
                		this.refreshEndGameStrategy.clear();
                	}
                	
                	if(!this.cancelPieceEndGameStrategy.isEmpty()){
                		for(Message_PP m: this.cancelPieceEndGameStrategy){
                			this.fireMessageReceived(m);
                		}
                		this.cancelPieceEndGameStrategy.clear();
                	}
                	
                	
                    int id;
                    if ((read = this.read(length)) > 0) {
                        l = Utils.byteArrayToInt(length);
                        if (l == 0) {
                            mess.setData(PeerProtocol.KEEP_ALIVE);
                        } else {
                            id = is.read();
                            if(id == -1){
                                System.err.println("id");
                                mess = null;
                                logger.warn("MessageReceiver is.read() == -1");
                            }else{
                                if (l == 1)
                                    mess.setData(id + 1);
                                else {
                                    l = l - 1;
                                    byte[] payload = new byte[l];
                                    if (this.read(payload) > 0)
                                        mess.setData(id + 1, payload);
                                    payload = null;
                                }
                            }
                        }
                    } else {
                        mess = null;
                    }
                }
            } catch (IOException ioe) {
                logger.warn("Error in MessageReceiver..."+ioe.getMessage()+" " + ioe.toString());
                this.fireMessageReceived(null);
                return;
                // m = null;
            } /*catch (InterruptedException ie) {
                this.fireMessageReceived(null);
                return;
                // m = null;
            } */catch (Exception e) {
            	logger.warn(l+" Error in MessageReceiver..."+e.getMessage()+" " + e.toString());
                this.fireMessageReceived(null);
                return;
                // m = null;
            }

            if(!this.hsOK){
                this.fireMessageReceived(hs);
                this.hsOK = true;
            }else{
                this.fireMessageReceived(mess);
            }
            // m = null;
        }
        //logger.warn("MessageReceiver Run ... " +this.run);
        try{
            this.dis.close();
            this.dis = null;
        }catch(Exception e){}

    }

    public void addIncomingListener(IncomingListener listener) {
        listeners.add(IncomingListener.class, listener);
    }

    public void removeIncomingListener(IncomingListener listener) {
        listeners.remove(IncomingListener.class, listener);
    }

    public IncomingListener[] getIncomingListeners() {
        return listeners.getListeners(IncomingListener.class);
    }

    protected void fireMessageReceived(Message m) {
        for (IncomingListener listener : getIncomingListeners()) {
            listener.messageReceived(m);
        }
    }

    /**
     * Stops the current thread by completing the run() method
     */
    public void stopThread(){
        this.run = false;
    }
}
