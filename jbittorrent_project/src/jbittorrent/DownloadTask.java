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
import java.util.*;
import java.net.*;
import javax.swing.event.EventListenerList;

import org.apache.log4j.Logger;

/**
 * Class representing a task that downloads pieces from a remote peer
 *
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class DownloadTask extends Thread implements IncomingListener,
        OutgoingListener {
	
    private static final int IDLE = 0;
    private static final int WAIT_HS = 1;
    private static final int WAIT_BFORHAVE = 2;
    private static final int WAIT_UNCHOKE = 4;
    private static final int READY_2_DL = 5;
    private static final int DOWNLOADING = 6;
    private static final int WAIT_BLOCK = 7;

    public static final int TASK_COMPLETED = 0;
    public static final int UNKNOWN_HOST = 1;
    public static final int CONNECTION_REFUSED = 2;
    public static final int BAD_HANDSHAKE = 3;
    public static final int MALFORMED_MESSAGE = 4;
    public static final int TIMEOUT = 5;
    public static final int CONNECTION_ALREADY_EXIST = 6;

    @SuppressWarnings("static-access")
	private int state = this.IDLE;
    private boolean run = true;
    private byte[] fileID;
    private byte[] myID;
    public Peer peer;

    private final boolean initiate;
    public byte[] bitfield = null;
    private boolean isDownloading = false;

    private boolean isStartedEndGame = false;
    private Piece downloadEndGamePiece = null;
    private LinkedList<Message_PP> pendingCancelMessageToSend;
    private LinkedList<Message_PP> cancelMessageReceiver;
    
    private Piece downloadPiece = null;
    private int offset = 0;

    private final EventListenerList listeners = new EventListenerList();

    private Socket peerConnection = null;
    private OutputStream os = null;
    private InputStream is = null;

    public MessageSender ms = null;
    public MessageReceiver mr = null;

    private long downloaded = 0;
    @SuppressWarnings("unused")
	private long uploaded = 0;
    @SuppressWarnings("unused")
	private long creationTime = 0;
    private long updateTime = 0;
    private long lmrt = 0;

    private LinkedList<Integer> pendingRequest;
    
    private static Logger logger = Logger.getLogger(DownloadManager.class);
    
    private String idTask;
    private int portSocket;
    
    private boolean isConected = false;
    
    private DownloadManager downloadManager;
    
    

    /**
     * Start the downloading process from the remote peer in parameter
     * @param p {@link Peer} The peer to connect to
     * @param fileID byte[] The file to be downloaded
     * @param myID byte[] The id of the current client
     * @param init boolean True if this client initiate the connection, false otherwise
     * @param s {@link Socket} Only set if this client receive a connection request from the remote peer
     * @param bitfield byte[] The piece currently ownend by this client
     */
    public DownloadTask(Peer p, byte[] fileID, byte[] myID, boolean init, byte[] bitfield, Socket s) {
    	
    	this.pendingCancelMessageToSend = new LinkedList<Message_PP>();
    	this.isStartedEndGame = false;
    	this.downloadEndGamePiece = null;
    	this.cancelMessageReceiver = new LinkedList<Message_PP>();
    	
        this.pendingRequest = new LinkedList<Integer>();
        this.fileID = fileID;
        this.myID = myID;
        this.initiate = init;
        this.bitfield = bitfield;
        
        String peerIP = null;
        this.portSocket = -1;
        this.is = null;
        this.os = null;
        this.idTask = null;
        
        try {
        	
            this.peerConnection = s;
            
            if(s != null){
            	
            	peerIP = this.peerConnection.getInetAddress().getHostAddress();
            	this.portSocket = this.peerConnection.getPort();
            	this.is = this.peerConnection.getInputStream();
                this.os = this.peerConnection.getOutputStream();
                this.setIdTask(this.peerConnection.getInetAddress().getHostAddress() + ":" + this.peerConnection.getPort());
                
            }
            
            if(p == null){
            	this.peer = new Peer();
                this.peer.setIP(peerIP);
                this.peer.setPort(this.peerConnection.getPort());
            }else{
            	this.peer = p;
            }
            
            if(this.portSocket == -1){
            	this.portSocket = this.peer.getPort();
            }
            
            if(this.idTask == null){
            	this.setIdTask(this.peer.toString());
            }
            
        } catch (IOException ioe) {
        	logger.warn("Error create downloadTask: " + ioe);
        }
    }

    /**
     * Start the downloading process from the remote peer in parameter
     * @param peer The peer to connect to
     * @param fileID The file to be downloaded
     * @param myID The id of the current client
     * @param init True if this client initiate the connection, false otherwise
     * @param bitfield The pieces currently owned by this client
     */
    public DownloadTask(Peer peer, byte[] fileID, byte[] myID, boolean init, byte[] bitfield) {
        this(peer, fileID, myID, init, bitfield, null);
    }

    /**
     * Start the downloading process from the remote peer in parameter
     * @param peer The peer to connect to
     * @param fileID The file to be downloaded
     * @param myID The id of the current client
     * @deprecated
     */

    public DownloadTask(Peer peer, byte[] fileID, byte[] myID) {
        this(peer, fileID, myID, true, null);
    }

    /**
     * Inits the connection to the remote peer. Also init the message sender and receiver.
     * If necessary, starts the handshake with the peer
     * @throws UnknownHostException If the remote peer is unknown
     * @throws IOException If the connection to the remote peer fails (reset, ...)
     */
    @SuppressWarnings("static-access")
	public void initConnection() throws UnknownHostException, IOException {
    	
    	logger.info(this.idTask + " InitConnection...");
    	
        if (this.peerConnection == null) {
        	
        	logger.info("New Socket: "+this.peer.getIP()+":"+ this.portSocket);
        	this.peerConnection = new Socket(this.peer.getIP(), this.portSocket);
            this.os = this.peerConnection.getOutputStream();
            this.is = this.peerConnection.getInputStream();
            this.peer.setConnected(true);
        }
        
        if(this.ms == null){
        	this.ms = new MessageSender(this.idTask, this.os);
            this.ms.addOutgoingListener(this);
            this.ms.start();
        }

        if(this.mr == null){
        	this.mr = new MessageReceiver(this.idTask, this.is);
            this.mr.addIncomingListener(this);
            this.mr.start();
        }
        
        this.fireAddActiveTask(peer.toString(), this);
        
        if (this.initiate && this.ms != null) {
            this.ms.addMessageToQueue(new Message_HS(this.fileID, this.myID));
            this.changeState(this.WAIT_HS);
        } else {
            this.changeState(this.WAIT_BFORHAVE);
        }
    }

    @SuppressWarnings("static-access")
	public void run() {
        try {
            this.initConnection();
            logger.info(this.idTask + " InitConnection ... running " + this.run);
            /**
             * Wait for the task to end, i.e. the peer to return to IDLE state
             */
            while (this.run)
                synchronized (this) {
                    this.wait();
                }
        } catch (UnknownHostException uhe) {
        	logger.warn(this.idTask + " DownloadTask IOException: " + uhe);
            this.fireTaskCompleted(this, this.UNKNOWN_HOST);
        } catch (IOException ioe) {
        	logger.warn(this.idTask + " DownloadTask IOException: " + ioe);
            this.fireTaskCompleted(this, this.CONNECTION_REFUSED);
        } catch (InterruptedException ie) {
        	logger.warn(this.idTask + " DownloadTask InterruptedException: " + ie);
        }
    }

    /**
     * Clear the piece currently downloading
     */
    private synchronized void clear() {
        if (downloadPiece != null) {
            this.firePieceRequested(downloadPiece.getIndex(), false);
            downloadPiece = null;
        }
    }

    /**
     * Returns this peer object
     * @return Peer
     */
    public synchronized Peer getPeer(){
        return this.peer;
    }

    /**
     * Request a peer to the peer
     * @param p The piece to be requested to the peer
     */
    @SuppressWarnings("static-access")
	public synchronized void requestPiece(Piece p) {
        synchronized (this) {
            if (this.state == this.READY_2_DL){
                this.downloadPiece = p;
            	//this.changeState(this.DOWNLOADING);
            }
                
        }
    }

    /**
     * Returns the total amount of bytes downloaded by this task so far
     * @return int
     */
    public synchronized int checkDownloaded(){
        int d = new Long(this.downloaded).intValue();
        //this.downloaded = 0;
        return d;
    }

    /**
     * Fired when the connection to the remote peer has been closed. This method
     * clear this task data and send a message to the DownloadManager, informing
     * it that this peer connection has been closed, resulting in the deletion of
     * this task
     */
    @SuppressWarnings("static-access")
	public synchronized void connectionClosed() {
    	logger.warn("connectionClosed (Connection Refused)... " + this.idTask);
        this.clear();
        this.fireTaskCompleted(this, this.CONNECTION_REFUSED);
    }

    /**
     * Fired when a keep-alive message has been sent by the MessageSender.
     * If at the time the keep-alive was sent, this peer has not received any
     * message from the remote peer since more that 3 minutes, the remote peer is
     * considered as dead, and we close the connection, then inform the
     * DownloadManager that this connection timed out...
     *
     * Otherwise, inform the DownloadManager that this task is still alive and has
     * not been used for a long time...
     */
    @SuppressWarnings("static-access")
	public synchronized void keepAliveSent() {
        if (System.currentTimeMillis() - this.lmrt > 180000) {
            this.clear();
            this.fireTaskCompleted(this, this.TIMEOUT);
            return;
        }
        this.firePeerReady(this.peer.toString());
    }

    /**
     * According to the message type, change the state of the task (peer) and
     * take the necessary actions
     * @param m Message
     */
    @SuppressWarnings("static-access")
	public synchronized void messageReceived(Message m) {

    	if(this.run){
    		
    		if (m == null) {
                this.fireTaskCompleted(this, this.MALFORMED_MESSAGE);
                return;
            }
    		
            this.lmrt = System.currentTimeMillis();

            if (m.getType() == PeerProtocol.HANDSHAKE) {
                Message_HS hs = (Message_HS) m;
                
                boolean alreadyExist = this.downloadManager.peerConnectionAlreadyExists(new String(hs.getPeerID()), this.peer);
                
                // Check that the requested file is the one this client is sharing
                if (Utils.bytesCompare(hs.getFileID(), this.fileID) && this.ms != null
                		&& !alreadyExist && !this.myID.equals(new String(hs.getPeerID()))) {
                    if (!initiate) { // If not already done, send handshake message
                        this.ms.addMessageToQueue(new Message_HS(this.fileID, this.myID));
                    }

                    this.peer.setIDTracker(new String(hs.getPeerID()));
                    this.ms.addMessageToQueue(new Message_PP(PeerProtocol.BITFIELD,this.bitfield));
                    this.creationTime = System.currentTimeMillis();
                    this.changeState(this.WAIT_BFORHAVE);
                    
                } else if(!alreadyExist){
                	
                    this.fireTaskCompleted(this, this.BAD_HANDSHAKE);
                    
                } else {
                	
                	this.fireTaskCompleted(this, this.CONNECTION_ALREADY_EXIST);
                }
                
                hs = null;

            } else {
            	
                Message_PP message = (Message_PP) m;
                
                int pieceIndexMessage = -1, beginBlockMessage = -1 , lengthBlockMessage = -1;
                int pieceIndexCancel = -1  ,beginBlockCancel = -1 , lengthBlockCancel = -1;
               
                switch (message.getType()) {
                case PeerProtocol.KEEP_ALIVE:
                    // Nothing to do, just keep the connection open
                    break;

                case PeerProtocol.CHOKE:
                    /*
                     * Change the choking state to true, meaning remote peer
                     * will not accept any request message from this client
                     */
                	
                	this.peer.setChoking(true);
                	
                    this.isDownloading = false;
                    
                    if (this.downloadPiece != null) {
                        this.changeState(this.READY_2_DL);
                    } 

                    break;

                case PeerProtocol.UNCHOKE:
                    /*
                     * Change the choking state to false, meaning this client now
                     * accepts request messages from this client.
                     * If this task was already downloading a piece, then continue.
                     * Otherwise, advertise DownloadManager that it is ready to do so
                     */
                	
                	this.peer.setChoking(false);
                	
                	if (this.downloadPiece == null) {
                        this.changeState(this.READY_2_DL);
                    } else {
                        this.changeState(this.DOWNLOADING);
                    }
                	
                    break;

                case PeerProtocol.INTERESTED:
                    /*
                     * Change the interested state of the remote peer to true,
                     * meaning this peer will start downloading from this client if
                     * it is unchoked
                     */
                	this.peer.setInterested(true);
                    break;

                case PeerProtocol.NOT_INTERESTED:
                    /*
                     * Change the interested state of the remote peer to true,
                     * meaning this peer will not start downloading from this client
                     * if it is unchoked
                     */
                	
                	this.peer.setInterested(false);
                	this.peer.setChoked(true);
                	
                    break;

                case PeerProtocol.HAVE:
                	
                    /*
                     * Update the peer piece list with the piece described in this
                     * message and advertise DownloadManager of the change
                     * 
                     */
                	
                    this.peer.setHasPiece(Utils.byteArrayToInt(message.
                            getPayload()), true);
                    this.firePeerAvailability(this.peer.toString(),
                                              this.peer.getHasPiece());
                    break;

                case PeerProtocol.BITFIELD:
                    /*
                     * Update the peer piece list with the piece described in this
                     * message and advertise DownloadManager of the change
                     */
                    this.peer.setHasPiece(message.getPayload());
                    this.firePeerAvailability(this.peer.toString(),
                                              this.peer.getHasPiece());
                    this.changeState(this.WAIT_UNCHOKE);
                    break;

                case PeerProtocol.REQUEST:
                    /*
                     * If the peer is not choked, advertise the DownloadManager of
                     * this request.Otherwise, end connection since the peer does
                     * not respect the Bittorrent protocol
                     */
                	
                	Message_PP mCancelDelete = null;
                	
                	pieceIndexMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(), 0, 4));
                	beginBlockMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(), 4, 4));
                	lengthBlockMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(), 8, 4));
                	
                	if(!this.cancelMessageReceiver.isEmpty()){
                    	
                    	for(Message_PP mCancel: this.cancelMessageReceiver){
                    		
                    		pieceIndexCancel = Utils.byteArrayToInt(Utils.subArray(mCancel.getPayload(),0, 4));
                        	beginBlockCancel = Utils.byteArrayToInt(Utils.subArray(mCancel.getPayload(), 4, 4));
                        	lengthBlockCancel = Utils.byteArrayToInt(Utils.subArray(mCancel.getPayload(), 8, 4));
                        	
                    		if(pieceIndexMessage == pieceIndexCancel && beginBlockMessage == beginBlockCancel && lengthBlockMessage == lengthBlockCancel){
                    			mCancelDelete = mCancel;
                    			break;
                    		}
                    		
                    	}
                    	
                	}
                		
                    if(!this.peer.isChoked() && mCancelDelete == null){
                    	
                        this.firePeerRequest(this.peer.toString(),pieceIndexMessage, beginBlockMessage, lengthBlockMessage);
                    
                    }else if(this.peer.isChoked() && mCancelDelete == null){
                    	
                    	if(this.peer.getNumSendChoke() < 3){
                    		ms.addMessageToQueue(new Message_PP(PeerProtocol.CHOKE));
                    		this.peer.setNumSendChoke(this.peer.getNumSendChoke()+1);
                    	}else{
                            this.fireTaskCompleted(this, this.MALFORMED_MESSAGE);
                    	}
                    	
                    }else{
                    	
                    	this.cancelMessageReceiver.remove(mCancelDelete);
                    			
                    }
                    
                    break;

                case PeerProtocol.PIECE:
                	
                	pieceIndexMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(),0, 4));
                	beginBlockMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(), 4, 4));
                	byte[] data = Utils.subArray(message.getPayload(), 8, message.getPayload().length - 8);
                	
                	
                    /**
                     * Sets the block of data downloaded in the piece block list and
                     * update the peer download rate. Removes the piece block from
                     * the pending request list and change state.
                     */
                	this.fireReceivedMessagePPPiece();
                	
                	/*
                	 * Eliminating potential cancellation messages EndGame mode.
                	 */
                	if(!this.pendingCancelMessageToSend.isEmpty()){
                		
                    	Message_PP mCancel = null;
                    	for(Message_PP mC: this.pendingCancelMessageToSend){
                    		
                    		pieceIndexCancel = Utils.byteArrayToInt(Utils.subArray(mC.getPayload(),0, 4));
                    		beginBlockCancel = Utils.byteArrayToInt(Utils.subArray(mC.getPayload(), 4, 4));
                    		if(pieceIndexMessage == pieceIndexCancel && beginBlockMessage == beginBlockCancel){
                    			mCancel = mC;
                    			break;
                    		}
                    	}
                    	if(mCancel != null){
                    		this.pendingCancelMessageToSend.remove(mCancel);
                    	}
                    	
                    }
                	
                	if(this.downloadPiece != null && this.downloadPiece.getIndex() == pieceIndexMessage){
                		
                		this.downloadPiece.setBlock(beginBlockMessage, data);
                        
                        this.peer.setDLRate(data.length);
                        this.pendingRequest.remove(new Integer(beginBlockMessage));
                        
                        if (this.pendingRequest.size() == 0)
                        	this.isDownloading = false;
                        
                        this.changeState(this.DOWNLOADING);
                        
                	}/*else{
                		logger.warn(this.peer.toString() + " Received Piece " + pieceIndexMessage + " ," + beginBlockMessage + ") and not currentDownloadPiece: "+this.downloadPiece);
                	}*/
                	
                    break;

                case PeerProtocol.CANCEL:
                	
                	pieceIndexMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(),0, 4));
                	beginBlockMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(), 4, 4));
                	this.cancelMessageReceiver.add((Message_PP) message.clone());
                	
                    break;

                case PeerProtocol.PORT:
                    // TODO: Still to implement the port message. Not used here
                    break;
                    
                case PeerProtocol.REFRESH_ENDGAME_STRATEGY:
                	/*
                	 * It is an internal message that doesn’t belong to the bittorrent protocol.  
                	 * The task has been advised of the currently selected pieces, was already 
                	 * downloaded by another task.  Therefore has to restart 
                	 * the process of selection of piece.
                	 */
                	
                	if (this.downloadPiece == null && !this.peer.isChoked()) {
                        this.changeState(this.READY_2_DL);
                    } else if(!this.peer.isChoked()){
                        this.changeState(this.DOWNLOADING);
                    }
                	
                	break;
                    
                case PeerProtocol.CAMCEL_PIECE_ENDGAME_STRATEGY:
                	/*
                	 * It is an internal message that does not belong to the bittorrent protocol. 
                	 * The task has been advised of the currently selected piece is already been downloaded 
                	 * by another peer.
                	 * Send cancellation messages blocks that had pending with the peer and resume the process 
                	 * of selection of piece.
                	 */
                	pieceIndexMessage = Utils.byteArrayToInt(Utils.subArray(message.getPayload(),0, 4));
                	
                	this.cancelPieceEndGameStrategy(pieceIndexMessage);
                	this.firePieceRequested(pieceIndexMessage, false);
                	
                	if(downloadEndGamePiece != null  && downloadEndGamePiece.getIndex() == pieceIndexMessage ){
						
                		this.downloadEndGamePiece = null;
                		this.isStartedEndGame = false;
                		
                		if(this.pendingRequest.size()!=0){
                			
                			this.pendingRequest.clear();
                    		offset = 0;
                    		this.clear();
        		    		
        		    		if(!this.peer.isChoked()) {
                                this.changeState(this.READY_2_DL);
                            }
                		}
					}
                	
                	break;
                }
                message = null;
            }
            
            m = null;
    	}
    	
    }

    /**
     * Change the state of the task. State depends on the previously received messages
     * This is here that are taken the most important decisions about the messages to
     * be sent to the remote peer
     * @param newState The new state of the download task
     */
    @SuppressWarnings("static-access")
	private synchronized void changeState(int newState) {
        this.state = newState;
        switch (newState) {

        case WAIT_BLOCK:
        	
            /**
             * Keep a certain number of unanswered requests, for performance.
             * If only sending 1 request an waiting, it is a loss of time and
             * bandwidth because of the RTT to the remote peer
             */
            if (this.pendingRequest.size() < 5 && offset < downloadPiece.getLength()){
                this.changeState(this.DOWNLOADING);
            }
            
            break;
        case READY_2_DL:
        	
            /**
             * Advertise the DownloadManager that this task is ready to download
             */
        	
        	if(!this.peer.isChoking()){
        		this.firePeerReady(this.peer.toString());
        		
        		if(this.downloadPiece != null)
        			changeState(DOWNLOADING);
        		
        	}else{
        		
        		this.pendingRequest.clear();
        		offset = 0;
        		this.clear();
        	}
        	
            break;
            
        case DOWNLOADING:
        	
            /**
             * If offset is bigger than the piece length and the pending request size
             * is 0, then we have downloaded all the piece blocks and we can verify
             * the integrity of the data
             */
        	
        	if (offset >= downloadPiece.getLength()) {
        		        		
                if (this.pendingRequest.size() == 0) {
                	
                    int p = downloadPiece.getIndex();
                    offset = 0;
                    
                    if (downloadPiece.verify()) {
                        this.firePieceCompleted(p, true);
                    } else {
                        this.firePieceCompleted(p, false);
                    }
                    
                    this.clear();
                    this.changeState(READY_2_DL);
                    
                }else if(this.peer.isChoking()){
                	this.pendingRequest.clear();
                	offset = 0;
            		this.clear();
                }
                
            } else if (downloadPiece != null && this.ms != null) {

            	if(!this.peer.isChoking()) {
        			
        			byte[] pieceIndex = Utils.intToByteArray(downloadPiece.getIndex());
                    byte[] begin = Utils.intToByteArray(offset);

                    int length = downloadPiece.getLength() - offset;
                    if (length >= PeerProtocol.BLOCK_SIZE)
                        length = PeerProtocol.BLOCK_SIZE;
                    
                    ms.addMessageToQueue(new Message_PP(PeerProtocol.REQUEST, Utils.concat(pieceIndex, Utils.concat(begin, Utils.intToByteArray(length))), 2));
                    
                    /*
                     * If the EndGame mode is active, are saved the messages of cancellation 
                     * of the blocks that are being requested.
                     */
                    if(this.isStartedEndGame && this.downloadEndGamePiece != null && this.downloadPiece.getIndex() == this.downloadEndGamePiece.getIndex()){
                		this.pendingCancelMessageToSend.add(new Message_PP(PeerProtocol.CANCEL, Utils.concat(pieceIndex, Utils.concat(begin, Utils.intToByteArray(length))), 2));
                    }
                    
                    if(this.updateTime == 0)
                        this.updateTime = System.currentTimeMillis();
                    
                    this.pendingRequest.add(new Integer(offset));
                    offset += PeerProtocol.BLOCK_SIZE;
                    this.isDownloading = true;
                    this.changeState(this.WAIT_BLOCK);
                    
        		}else{
        			this.pendingRequest.clear();
        			offset = 0;
            		this.clear();
        		}
            	
            }
        	
            break;
        }
    }

    public synchronized void addDTListener(DTListener listener) {
        listeners.add(DTListener.class, listener);
    }

    public synchronized void removeDTListener(DTListener listener) {
        listeners.remove(DTListener.class, listener);
    }

    public synchronized DTListener[] getDTListeners() {
        return listeners.getListeners(DTListener.class);
    }

    /**
     * Fired to inform if the given piece is requested or not...
     * @param piece int
     * @param requested boolean
     */
    private synchronized void firePieceRequested(int piece,
                                                 boolean requested) {
        for (DTListener listener : getDTListeners()) {
            listener.pieceRequestActive(piece, requested);
        }
    }

    /**
     * Fired to inform that the given piece has been completed or not
     * @param piece int
     * @param complete boolean
     */
    private synchronized void firePieceCompleted(int piece,
                                                 boolean complete) {
        for (DTListener listener : getDTListeners()) {
            listener.pieceCompleted(this.peer.toString(), piece, complete, this);
        }
    }
    
    /**
     * Fired to inform that have received a block of peer
     */
    private synchronized void fireReceivedMessagePPPiece() {
    	for (DTListener listener : getDTListeners()) {
    		listener.receivePieceBlock(this.peer.toString());
    	}
    }
    
    /**
     * Fired to inform that the task is finished for a certain reason
     * @param dt DownloadTask
     * @param reason Reason why the task ended
     */
    private synchronized void fireTaskCompleted(DownloadTask dt, int reason) {
    	this.end();
        for (DTListener listener : getDTListeners()) {
            listener.taskCompleted(dt, reason);
        }
    }

    /**
     * Fired to inform that this task is ready to download
     * @param id String
     */
    private synchronized void firePeerReady(String id) {
        for (DTListener listener : getDTListeners()) {
            listener.peerReady(id);
        }
    }

    /**
     * Fired to inform that the peer requests a piece block
     * @param peerID String
     * @param piece int
     * @param begin int
     * @param length int
     */
    private synchronized void firePeerRequest(String peerID, int piece, int begin, int length) {
        for (DTListener listener : getDTListeners()) {
            listener.peerRequest(peerID, piece, begin, length, this);
        }

    }

    /**
     * Fired to inform that the availability of this peer has changed
     * @param id String
     * @param hasPiece BitSet
     */
    private synchronized void firePeerAvailability(String id,
            BitSet hasPiece) {
        for (DTListener listener : getDTListeners()) {
            listener.peerAvailability(id, hasPiece);
        }
    }

    /**
     * Fired to inform that this task has completed the handshake and is now
     * ready to communicate with the remote peer
     * @param id String
     * @param dt DownloadTask
     */
    private synchronized void fireAddActiveTask(String id, DownloadTask dt) {
        for (DTListener listener : getDTListeners()) {
            listener.addActiveTask(id, dt);
        }
    }
    
    public void end(){
    	this.clear();
    	if(this.run){
    		this.endTask();
    	}else{
    		logger.warn(this.idTask + " ...already end");
    	}
    }

    /**
     * Stops this thread by setting the 'run' variable to false and closing
     * the communication thread (Message receiver and sender). Closes the
     * connection to the remote peer
     */
    @SuppressWarnings("static-access")
	public synchronized void endTask(){
    	
        synchronized(this){
        	
        	logger.warn("End Task ... " + this.idTask);
        	
        	this.changeState(this.IDLE);
            this.run = false;
            
            if(this.ms != null){
            	//logger.warn(this.idTask + " Sender stop...");
            	this.ms.stopThread();
            	this.ms = null;
            }
            
            if(this.mr != null){
            	//logger.warn(this.idTask + " Receiver stop...");
            	this.mr.stopThread();
            	this.mr = null;
            }
            
            try{
            	//logger.warn(this.idTask + " PeerConnection close...");
            	if(this.peerConnection != null && !this.peerConnection.isClosed()){
            		this.peerConnection.close();
            	}
        		this.peerConnection = null;
            }catch(Exception e){
            	
            	logger.warn("Exception DownloadTask end() "+e);
            }
            
            try{
            	//logger.warn(this.idTask + " InputStream close...");
            	if(this.is != null){
            		this.is.close();
            	}
            	this.is = null;
            }catch(Exception e){
            	logger.warn("Exception DownloadTask end() "+e);
            }finally{
            	this.is = null;
            }
            
            try{
            	//logger.warn(this.idTask + " OutputStream close...");
            	if(this.os != null){
            		this.os.close();
            	}
            	this.os = null;
            }catch(Exception e){
            	logger.warn("Exception DownloadTask end() "+e);
            }finally{
                this.os = null;
            }
            
            this.peerConnection = null;
            

        	//logger.warn(this.idTask + " All closed");
            this.notifyAll();
            //logger.warn(this.idTask + " Notify All...");
        }
    }

    protected void finalize() throws Throwable{
        if(this.peerConnection != null){
            try{
                try{
                    if(this.is != null){
                    	//logger.warn(this.idTask + " finalize-InputStream close...");
                    	this.is.close();
                    }
                }finally{
                    this.is = null;
                }
                try{
                    if(this.os != null){
                    	//logger.warn(this.idTask + " finalize-InputStream close...");
                    	this.os.close();
                    }
                }finally{
                    this.os = null;
                }

                logger.warn(this.idTask + " finalize-PeerConnection close...");
                
                if (this.peerConnection != null && !this.peerConnection.isClosed())
                    this.peerConnection.close();
                this.peerConnection = null;

            }finally{
                super.finalize();
            }
        }
    }

	public boolean isDownloading() {
		return isDownloading;
	}

	public String getIdTask() {
		return idTask;
	}

	public void setIdTask(String idTask) {
		this.idTask = idTask;
	}

	public int getPortSocket() {
		return portSocket;
	}

	public void setPortSocket(int port) {
		this.portSocket = port;
	}

	public boolean isConected() {
		return isConected;
	}

	public void setConected(boolean isConected) {
		this.isConected = isConected;
	}
	
	public boolean isDownloadPiece(){
		
		if(this.downloadPiece != null){
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isRunning(){
		return this.run;
	}

	public boolean isStartedEndGame() {
		return isStartedEndGame;
	}
	
	public int getIndexDowloadEndGamePiece(){
		int currentIndexEndGamePiece = -1;
		if(this.downloadEndGamePiece != null){
			currentIndexEndGamePiece = this.downloadEndGamePiece.getIndex();
		}
		return currentIndexEndGamePiece;
	}
	
	public int getIndexDownloadPiece(){
		if(this.downloadPiece != null){
			return this.downloadPiece.getIndex();
		}else{
			return -1;
		}
	}
	
	/**
	 * Sends the messages of cancellation of blocks that have been solicited.  
	 * These blocks are of the piece that belongs to index that is passed by parameter. 
	 * @param indexEndGamePiece Index of the selected piece, when End Game mode was activated. 
	 */
	public synchronized void cancelPieceEndGameStrategy(int indexEndGamePiece){
		
		//synchronized (this.pendingCancelMessageToSend) {
			if(!this.pendingCancelMessageToSend.isEmpty()){
				LinkedList<Message_PP> listCancel = new LinkedList<Message_PP>();
				int pieceIndex;
				for(Message_PP m: this.pendingCancelMessageToSend){
					pieceIndex = Utils.byteArrayToInt(Utils.subArray(m.getPayload(),0, 4));
					if(indexEndGamePiece == pieceIndex){
						//logger.info(this.idTask + " Send message Cancel (" + Utils.byteArrayToInt(Utils.subArray(m.getPayload(),0, 4)) + ", " + Utils.byteArrayToInt(Utils.subArray(m.getPayload(),4, 4)) + ")");
						ms.addMessageToQueue(m);
					}else{
						listCancel.add(m);
					}
				}
				this.pendingCancelMessageToSend = listCancel;
			}
		//}
		
	}
	

	/**
	 * Establishes a new piece for the End Game mode.  If it already there was 
	 * a selected piece for the End Game mode, it will send the messages of 
	 * cancellation of the solicited blocks that still is waited to receive.
	 * @param downloadEndGamePiece {@link Piece}: selected piece for the 
	 * End Game mode.  If the value is equal to NULL disables the End Game mode.
	 */
	public synchronized void setStartedEndGame(Piece downloadEndGamePiece) {

		try{
			int currentIndexEndGamePiece = -1;
			if(this.downloadEndGamePiece != null){
				currentIndexEndGamePiece = this.downloadEndGamePiece.getIndex();
			}
			
			this.downloadEndGamePiece = downloadEndGamePiece;
			if(this.downloadEndGamePiece != null){
				this.isStartedEndGame = true;
			}else{
				this.isStartedEndGame = false;
			}
			
			if(currentIndexEndGamePiece != -1 && ( downloadEndGamePiece == null || (downloadEndGamePiece.getIndex() != currentIndexEndGamePiece) ) ){
				this.cancelPieceEndGameStrategy(currentIndexEndGamePiece);
			}
		}catch(NullPointerException ex){
    		logger.error("setStartedEndGame exception: " + ex);
    	}catch(Exception e){
    		logger.error("setStartedEndGame exception: " + e);
    		e.printStackTrace();
    	}
		
	}

	public DownloadManager getDownloadManager() {
		return downloadManager;
	}

	public void setDownloadManager(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}

}
