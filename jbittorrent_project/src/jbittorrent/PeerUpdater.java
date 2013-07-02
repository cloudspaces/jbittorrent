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

import javax.swing.event.EventListenerList;

import org.apache.log4j.Logger;

import java.net.UnknownHostException;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Class providing methods to enable communication between the client and a tracker.
 * Provide method to decode and parse tracker response.
 *
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 */
public class PeerUpdater extends Thread {
    private LinkedHashMap<String, Peer> peerList;
    private byte[] id;
    private TorrentFile torrent;


    private long downloaded = 0;
    private long uploaded = 0;
    private long left = 0;
    private String event = "&event=started";
    private int listeningPort = 6881;
    
    private int interval = 10;
    private int minInterval = 0;
    private boolean first = true;
    private boolean end = false;
    private boolean complete = false;
    
    private static Logger logger = Logger.getLogger(DownloadManager.class);
    
    private String ipAddress = null;


    private final EventListenerList listeners = new EventListenerList();

    public PeerUpdater(byte[] id, TorrentFile torrent, int intervalUpdateListPeers) {
        peerList = new LinkedHashMap<String, Peer>();
        this.id = id;
        this.torrent = torrent;
        this.left = torrent.total_length;
        this.setDaemon(true);
        this.interval = intervalUpdateListPeers;
        //this.start();
    }
    
    public boolean isEnd(){
    	return this.end;
    }

    public void setListeningPort(int port){
        this.listeningPort = port;
    }
    
    public int getListeningPort(){
    	return this.listeningPort;
    }

    /**
     * Returns the last interval for updates received from the tracker
     * @return int
     */
    public int getInterval() {
        return this.interval;
    }

    /**
     * Returns the last minimal interval for updates received from the tracker
     * @return int
     */

    public int getMinInterval() {
        return this.minInterval;
    }

    /**
     * Returns the number of bytes that have been downloaded so far
     * @return int
     */

    public long getDownloaded() {
        return this.downloaded;
    }

    /**
     * Returns the number of bytes that have been uploaded so far
     * @return int
     */

    public long getUploaded() {
        return this.uploaded;
    }

    /**
     * Returns the number of bytes still to download to complete task
     * @return int
     */

    public long getLeft() {
        return this.left;
    }

    /**
     * Returns the current event of the client
     * @return int
     */

    public String getEvent() {
        return this.event;
    }

    /**
     * Sets the interval between tracker update
     * @param interval int
     */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * Sets the mininterval between tracker update
     * @param minInt int
     */
    public void setMinInterval(int minInt) {
        this.minInterval = minInt;
    }

    /**
     * Sets the # of bytes downloaded so far
     * @param dl long
     */

    public void setDownloaded(long dl) {
        this.downloaded = dl;
    }

    /**
     * Sets the # of bytes uploaded so far
     * @param ul long
     */
    public void setUploaded(long ul) {
        this.uploaded = ul;
    }

    /**
     * Sets the # of bytes still to download
     * @param left long
     */
    public void setLeft(long left) {
        this.left = left;
    }

    /**
     * Sets the current state of the client
     * @param event String
     */
    public void setEvent(String event) {
        this.event = event;
    }

    /**
     * Returns the list of peers in its current state
     * @return LinkedHashMap
     */
    public LinkedHashMap<String, Peer> getList() {
        return this.peerList;
    }

    /**
     * Update the parameters for future tracker communication
     * @param dl int
     * @param ul int
     * @param event String
     */
    public synchronized void updateParameters(int dl, int ul, String event) {
        synchronized (this) {
            this.downloaded += dl;
            this.uploaded += ul;
            this.left -= dl;
            this.event = event;
        }
    }

    /**
     * Thread method that regularly contact the tracker and process its response
     */
    public void run() {
        //int tryNB = 0;
		byte[] b = new byte[0];
        while (!this.end) {
            //tryNB++;
            
            logger.info("ContactTracker ...................");
            
            this.peerList = this.processResponse(this.contactTracker(id,
                    torrent, this.downloaded,
                    this.uploaded,
                    this.left, this.event));
            
            if(torrent.scrapeURL != null){
            	Map<String, Integer> mapScrape = this.contactTrackerScrapeGetInfoPeers(torrent);
            	if(mapScrape != null){
            		this.fireUpdatePeerListState(mapScrape);
            	}
            }	
            
            if (peerList != null) {
                if (first) {
                    this.event = "";
                    first = false;
                }
                //tryNB = 0;
                this.fireUpdatePeerList(this.peerList);
                try {
                	Thread.sleep(this.interval * 1000);
                    synchronized (b) {
                        b.wait(interval * 1000);
                    }
                } catch (InterruptedException ie) {}
            } else {
                try {
                	Thread.sleep(2000);
                    synchronized (b) {
                        b.wait(2000);
                    }
                } catch (InterruptedException ie) {}
            }
        }
    }

    /**
     * Process the map representing the tracker response, which should contain
     * either an error message or the peers list and other information such as
     * the interval before next update, aso
     * @param m The tracker response as a Map
     * @return LinkedHashMap A HashMap containing the peers and their ID as keys
     */
    public synchronized LinkedHashMap<String, Peer> processResponse(Map m) {
        LinkedHashMap<String, Peer> l = null;
        
        
        if (m != null) {
            if (m.containsKey("failure reason")) {
                this.fireUpdateFailed(0,
                                      "The tracker returns the following error message:" +
                                      "\t'" +
                                      new String((byte[]) m.get(
                                              "failure reason")) +
                                      "'");
                return null;
            } else {
                if (((Long) m.get("interval")).intValue() < this.interval)
                    this.interval = ((Long) m.get("interval")).intValue();
                /*else
                    this.interval *= 2;*/

                Object peers = m.get("peers");
                ArrayList peerList = new ArrayList();
                l = new LinkedHashMap<String, Peer>();
                if (peers instanceof List) {
                    peerList.addAll((List) peers);
                    if (peerList != null && peerList.size() > 0) {
                        for (int i = 0; i < peerList.size(); i++) {
                            String peerID = new String((byte[]) ((Map) (
                                    peerList.
                                    get(i))).
                                    get(
                                            "peer_id"));
                            String ipAddress = new String((byte[]) ((Map) (
                                    peerList.
                                    get(
                                            i))).
                                    get("ip"));
                            
                            int state = ((Long) ((Map) (peerList.get(i))).get(
                                    "state")).intValue();
                            
                            int port = ((Long) ((Map) (peerList.get(i))).get(
                                    "port")).intValue();
                            Peer p = new Peer(peerID, ipAddress, port);
                            p.setListeningPort(port);
                            
                            if(this.ipAddress == null || !this.ipAddress.equals(ipAddress) || this.listeningPort != port){
                            	l.put(p.toString(), p);
                            }
                            
                        }
                    }
                } else if (peers instanceof byte[]) {
                	
                	String listPeer = "";
                    byte[] p = ((byte[]) peers);
                    for (int i = 0; i < p.length; i += 6) {
                    	
                        Peer peer = new Peer();
                        peer.setIP(Utils.byteToUnsignedInt(p[i]) + "." +
                                   Utils.byteToUnsignedInt(p[i + 1]) + "." +
                                   Utils.byteToUnsignedInt(p[i + 2]) + "." +
                                   Utils.byteToUnsignedInt(p[i + 3]));
                        peer.setPort(Utils.byteArrayToInt(Utils.subArray(p,
                                i + 4, 2)));
                        if(this.ipAddress == null || !this.ipAddress.equals(peer.getIP()) || this.listeningPort != peer.getPort()){
                        	peer.setListeningPort(peer.getPort());
                        	l.put(peer.toString(), peer);
                        	listPeer = listPeer + "(" + peer.toString() + "), ";
                        }
                        
                    }
                    logger.info("Contact tracker listPeer: "+listPeer);
                }
            }
            return l;
        } else
            return null;
    }

    /**
     * Contact the tracker according to the HTTP/HTTPS tracker protocol and using
     * the information in the TorrentFile.
     * @param id byte[]
     * @param t TorrentFile
     * @param dl long
     * @param ul long
     * @param left long
     * @param event String
     * @return A Map containing the decoded tracker response
     */
    public synchronized Map contactTracker(byte[] id,
                                           TorrentFile t, long dl, long ul,
                                           long left, String event) {
        try {
            URL source = new URL(t.announceURL + "?info_hash=" +
                                 t.info_hash_as_url + "&peer_id=" +
                                 Utils.byteArrayToURLString(id) + "&port="+
                                this.listeningPort +
                                 "&downloaded=" + dl + "&uploaded=" + ul +
                                 "&left=" +
                                 left + "&numwant=100&compact=1" + event);
            
           // System.out.println("Contact Tracker. URL source = " + source);
            
            URLConnection uc = source.openConnection();
            InputStream is = uc.getInputStream();

            BufferedInputStream bis = new BufferedInputStream(is);

            // Decode the tracker bencoded response
            Map m = BDecoder.decode(bis);
            //System.out.println("contactTracker"+m);
            bis.close();
            is.close();

            return m;
        } catch (MalformedURLException murle) {
            this.fireUpdateFailed(2,
                                  "Tracker URL is not valid... Check if your data is correct and try again");
        } catch (UnknownHostException uhe) {
            this.fireUpdateFailed(3, "Tracker not available... Retrying...");
        } catch (IOException ioe) {
            this.fireUpdateFailed(4, "Tracker unreachable... Retrying");
        } catch (Exception e) {
            this.fireUpdateFailed(5, "Internal error");
        }
        return null;
    }


    /**
     * Contact the tracker according to the HTTP/HTTPS tracker protocol and using
     * the information in the TorrentFile. Checks if all peers are seeds.
     * @param t TorrentFile (scrapeURL)
     * @return A Map containing the decoded tracker response (keys = complete, downloaded, incomplete)
     */
	public synchronized Map<String, Integer> contactTrackerScrapeGetInfoPeers(TorrentFile t) {
    	
    	Map<String, Integer> mFileInfoPeers = null;
    	
    	if(t.scrapeURL == null){
    		if(t.announceURL.indexOf("/announce") != -1){
    			t.scrapeURL = t.announceURL.replaceAll("/announce","/scrape");
    			logger.info("ScrapeURL: " + t.scrapeURL);
    		}
    	}
    	
    	if(t.scrapeURL != null){
    		try {
            	
                URL source = new URL(t.scrapeURL + "?info_hash=" + t.info_hash_as_url);
                URLConnection uc = source.openConnection();
                InputStream is = uc.getInputStream();

                BufferedInputStream bis = new BufferedInputStream(is);

                // Decode the tracker bencoded response
                Map<?, ?> mFilesInfoHash = BDecoder.decode(bis);
                if(mFilesInfoHash.containsKey("files")){
                	Map<?, ?> mFile = (Map<?, ?>) mFilesInfoHash.get("files");
                	Iterator<?> it =  mFile.keySet().iterator();
                	String hash = null;
                	Object key = null;
                	while(it.hasNext() && key == null){
                		key = it.next();
                		hash = key.toString();
                		if(!Utils.bytesToHex(hash.getBytes()).toUpperCase().equals(Utils.byteArrayToByteString(hash.getBytes()))){
                			key = null;
                		}
                	}
                	
                	if(key != null && mFile.containsKey(key)){
                		Map<?, ?> mp =  (Map<?, ?>) mFile.get(key);
                		Iterator<?> itmap =  mp.keySet().iterator();
                		mFileInfoPeers = new HashMap<String, Integer>();
                		Object kmap;
                		while(itmap.hasNext()){
                			kmap = itmap.next();
                			mFileInfoPeers.put(kmap.toString(), Integer.valueOf(mp.get(kmap).toString()));
                		}
                	}
                }
                
                bis.close();
                is.close();

                return mFileInfoPeers;
            } catch (MalformedURLException murle) {
                this.fireUpdateFailed(2, "Tracker URL is not valid... Check if your data is correct and try again");
            } catch (UnknownHostException uhe) {
                this.fireUpdateFailed(3, "Tracker not available... Retrying...");
            } catch (IOException ioe) {
                this.fireUpdateFailed(4, "Tracker unreachable... Retrying");
            } catch (Exception e) {
                this.fireUpdateFailed(5, "Internal error");
            }
    	}
    	
        return mFileInfoPeers;
    }
    
    
    

    /**
     * Stops the update process. This methods sends one last message to
     * the tracker saying this client stops sharing the file and it also exits
     * the run method
     */
    public void end() {
    	logger.info("contactTracker STOPPED PeerUpdater");
        this.event = "&event=stopped";
        this.end = true;
        this.contactTracker(this.id, this.torrent, this.downloaded, this.uploaded, this.left, "&event=stopped");
    }
    
    /**
     * This method sends one message to the tracker saying 
     * this client stops downloading the file and  sharing the file now.
     */
    public void completed() {
    	if(!this.complete){
    		this.event = "&event=completed";
        	this.complete = true;
            this.contactTracker(this.id, this.torrent, this.downloaded, this.uploaded, this.left, "&event=completed");
    	}
    }

    /**
     * Adds a PeerUpdateListener to the list of listeners, enabling communication
     * with this object
     * @param listener PeerUpdateListener
     */
    public void addPeerUpdateListener(PeerUpdateListener listener) {
        listeners.add(PeerUpdateListener.class, listener);
    }

    /**
     * Removes a PeerUpdateListener from the list of listeners
     * @param listener PeerUpdateListener
     */

    public void removePeerUpdateListener(PeerUpdateListener listener) {
        listeners.remove(PeerUpdateListener.class, listener);
    }

    /**
     * Returns the list of object that are currently listening to this PeerUpdater
     * @return PeerUpdateListener[]
     */
    public PeerUpdateListener[] getPeerUpdateListeners() {
        return listeners.getListeners(PeerUpdateListener.class);
    }

    /**
     * Sends a message to all listeners with a HashMap containg the list of all
     * peers present in the last tracker response
     * @param l LinkedHashMap
     */
    protected void fireUpdatePeerList(LinkedHashMap<String, Peer> l) {
        for (PeerUpdateListener listener : getPeerUpdateListeners()) {
            listener.updatePeerList(l);
        }
    }

    /**
     * Sends a message to all listeners with an error code and a String representing
     * the reason why the last try to contact tracker failed
     * @param error int
     * @param message String
     */

    protected void fireUpdateFailed(int error, String message) {
        for (PeerUpdateListener listener : getPeerUpdateListeners()) {
            listener.updateFailed(error, message);
        }
    }
    
    /**
     * Sends the information of the state of the swarm of peers. 
     * @param mapState is a Map<String, Integer> with the keys, <b>complete<b> 
     * is the number of peers that are seeds and <b>incomplete<b> is the number 
     * of peers that are downloading the file.
     */
    protected void fireUpdatePeerListState(Map<String, Integer> mapState) {
        for (PeerUpdateListener listener : getPeerUpdateListeners()) {
            listener.updatePeerListState(mapState);
        }
    }

	public boolean isComplete() {
		return complete;
	}

	public void setComplete(boolean complete) {
		this.complete = complete;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

}
