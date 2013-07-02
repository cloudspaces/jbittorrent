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

import java.util.*;

/**
 * Class representing a bittorrent peer
 *
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class Peer {
	
    private String idTracker = null;
    
    private String ip;
    
    private int port;
    
    private int listeningPort;
    
    private boolean interested = false;
    private boolean choked = true;
    private boolean interesting = false;
    private boolean choking = true;
    private BitSet hasPiece;
    private int downloaded = 0;
    private float dlrate = 0;
	private long lastDL = 0;
    private float ulrate = 0;
	private long lastUL = 0;
    private int uploaded = 0;
    private boolean connected = false;
    
    private int numSendChoke = 0;
    
    //For logs
    private int numPiecesDownloadedByThis = 0;
    private int numPiecesSentToThis = 0;

    public Peer() {
        this.hasPiece = new BitSet();
        this.idTracker = null;
        this.listeningPort = -1;
        
        this.numPiecesDownloadedByThis = 0;
        this.numPiecesSentToThis = 0;
    }

    public Peer(String idTracker, String ip, int port){
    	this.numSendChoke = 0;
        this.setLastDL(System.currentTimeMillis());
        this.setLastUL(System.currentTimeMillis());
        this.idTracker = idTracker;
        this.ip = ip;
        this.port = port;
        this.hasPiece = new BitSet();
        this.listeningPort = -1;
        
        this.numPiecesDownloadedByThis = 0;
        this.numPiecesSentToThis = 0;
    }

    public void resetDL(){
        this.dlrate = 0;
        this.setLastDL(System.currentTimeMillis());
    }

    public void resetUL(){
        this.ulrate = 0;
        this.setLastUL(System.currentTimeMillis());
    }

    /**
     * Returns the number of bytes downloaded since the last reset
     * @param reset true if the download rate should be reset
     * @return float
     */
    public float getDLRate(boolean reset){
        if(reset){
            float tmp = this.dlrate;
            this.dlrate = 0;
            return tmp;
        }else
            return this.dlrate;

    }

    /**
     * Returns the number of bytes uploaded since the last reset.
     * @param reset true if the download rate should be reset
     * @return float
     */
    public float getULRate(boolean reset){
        if(reset){
            float tmp = this.ulrate;
            this.ulrate = 0;
            return tmp;
        }else
            return this.ulrate;
    }

    /**
     * Returns the total number of bytes downloaded from this peer
     * @return int
     */
    public int getDL(){
        return this.downloaded;
    }

    /**
     * Returns the total number of bytes uploaded to this peer
     * @return int
     */
    public int getUL(){
        return this.uploaded;
    }

    /**
     * Updates the downloaded values
     * @param dl int
     */
    public void setDLRate(int dl){
        this.dlrate += dl;
        this.downloaded += dl;
    }

    /**
     * Updates the uploaded values
     * @param ul int
     */
    public void setULRate(int ul){
        this.ulrate += ul;
        this.uploaded += ul;
    }

    /**
     * Returns the id of this peer
     * @return String
     */
    public String getIDTracker(){
        return this.idTracker;
    }

    /**
     * Returns the IP address of this peer
     * @return String
     */
    public String getIP(){
        return this.ip;
    }

    /**
     * Returns the listening port of this peer
     * @return int
     */
    public int getPort(){
        return this.port;
    }

    /**
     * Returns the pieces availability of this peer
     * @return BitSet
     */
    public BitSet getHasPiece(){
        return this.hasPiece;
    }

    /**
     * Sets the id of this peer
     * @param idTracker String
     */
    public void setIDTracker(String idTracker){
        this.idTracker = idTracker;
    }

    /**
     * Sets the IP address of this peer
     * @param ip String
     */
    public void setIP(String ip){
        this.ip = ip;
    }

    /**
     * Sets the listening port of this peer
     * @param port int
     */
    public void setPort(int port){
        this.port = port;
    }
    /**
     * Returns if this peer is interested or not
     * @return boolean
     */
    public boolean isInterested(){
        return this.interested;
    }

    /**
     * Returns if this peer is choked or not
     * @return boolean
     */
    public boolean isChoked(){
        return this.choked;
    }

    /**
     * Returns if this peer is interesting or not
     * @return boolean
     */
    public boolean isInteresting(){
        return this.interesting;
    }

    /**
     * Returns if this peer is choking or not
     * @return boolean
     */
    public boolean isChoking(){
        return this.choking;
    }

    /**
     * Sets if this peer is intereseted or not
     * @param i boolean
     */
    public void setInterested(boolean i){
        this.interested = i;
    }

    /**
     * Sets if this peer is choked or not
     * @param c boolean
     */
    public void setChoked(boolean c){
    	this.numSendChoke = 0;
        this.choked = c;
    }

    /**
     * Sets if this peer is interesting or not
     * @param i boolean
     */
    public void setInteresting(boolean i){
        this.interesting = i;
    }

    /**
     * Sets if this peer is choking or not
     * @param c boolean
     */
    public void setChoking(boolean c){
        this.choking = c;
    }

    /**
     * Updates this peer availability according to the received bitfield
     * @param bitfield byte[]
     */
    public void setHasPiece(byte[] bitfield){
        boolean[] b = Utils.byteArray2BitArray(bitfield);
        for(int i = 0; i < b.length; i++){
        	//System.out.println("hasPiece set: "+i+","+b[i]);
            this.hasPiece.set(i,b[i]);
        }
        //System.out.println("numPiece:"+ b.length);
    }

    /**
     * Updates the availability of the piece in parameter
     * @param piece int
     * @param has boolean
     */
    public void setHasPiece(int piece, boolean has){
        this.hasPiece.set(piece, has);
    }

    public boolean isConnected(){
        return this.connected;
    }

    public void setConnected(boolean connectionStatus){
        this.connected = connectionStatus;
    }

    /**
     * Compares if this peer is equal to the peer in parameter
     * @param p Peer
     * @return boolean
     */
    public boolean equals(Peer p){
        if(this.idTracker == p.getIDTracker() && this.ip == p.getIP() && this.port == p.getPort() && this.listeningPort == p.getListeningPort())
            return true;
        return false;
    }

    /**
     * Returns this peer characteristics in the form <ip address>:<port>
     * @return String
     */
    public String toString(){
        return (this.ip+":" + this.port);
    }

	public int getNumSendChoke() {
		return numSendChoke;
	}

	public void setNumSendChoke(int numMalformed) {
		this.numSendChoke = numMalformed;
	}

	/**
	 * Get listening port of this peer announced in the tracker.
	 * @return int
	 */
	public int getListeningPort() {
		return listeningPort;
	}

	/**
	 * Sets the listening port of this peer announced in the tracker.
	 * @param listeningPort int
	 */
	public void setListeningPort(int listeningPort) {
		this.listeningPort = listeningPort;
	}

	/**
	 * Get the number of parts obtained by this peer.
	 * @return int
	 */
	public int getNumPiecesDownloadedByThis() {
		return numPiecesDownloadedByThis;
	}

	public void setNumPiecesDownloadedByThis(int nPiecesDownloadedByThis) {
		this.numPiecesDownloadedByThis = nPiecesDownloadedByThis;
	}

	/**
	 * Get the number of blocks sent to the peer.
	 * @return int
	 */
	public int getNumPiecesSentToThis() {
		return numPiecesSentToThis;
	}

	public void setNumPiecesSentToThis(int nPiecesSentToThis) {
		this.numPiecesSentToThis = nPiecesSentToThis;
	}

	public long getLastDL() {
		return lastDL;
	}

	public void setLastDL(long lastDL) {
		this.lastDL = lastDL;
	}

	public long getLastUL() {
		return lastUL;
	}

	public void setLastUL(long lastUL) {
		this.lastUL = lastUL;
	}

	
}
