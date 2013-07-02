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

package trackerBT;

import java.io.*;
import java.util.*;
import java.net.*;
import org.jdom.*;
import org.jdom.filter.*;
import org.jdom.input.*;
import org.jdom.output.*;
import org.jdom.output.Format;
import simple.http.*;
import simple.http.load.*;
import simple.http.serve.*;

/**
 * Service called to answer a peer requesting information about peers sharing a given torrent
 */
public class TrackerService extends Service {
    private static final int DONE = 0;
    private static final int UNKNOWN_TORRENT = 1;
    private static final int MISSING_PEERID = 2;
    private static final int MISSING_FILEID = 3;
    private static final int MISSING_PORT = 4;
    private static final int MISSING_UL = 5;
    private static final int MISSING_DL = 6;
    private static final int MISSING_LEFT = 7;
    private static final int MISSING_EVENT = 8;
    private static final int INTERNAL_ERROR = 9;
    private static final int MALFORMED_REQUEST = 10;

    private static final String[] MESSAGE = {
                                            "The requested torrent is not listed on this tracker",
                                            "Missing parameter in request: peer_id",
                                            "Missing parameter in request: info_hash",
                                            "Missing parameter in request: port",
                                            "Missing parameter in request: uploaded",
                                            "Missing parameter in request: downloaded",
                                            "Missing parameter in request: left",
                                            "Missing parameter in request: event",
                                            "Request failed due to tracker internal error. Try again later",
                                            "Malformed request..."};

    Document peerList;

    /**
     * Default constructor for the tracker service, requested by the Simple server
     * @param context Context that was provided to the Simple server
     */
    public TrackerService(Context context) {
        super(context);
    }

    /**
     * Method called when a request is received at the tracker address
     * It constructs the response message according to the parameters of the request.
     *
     * @param req Request message received from a peer with its parameters.
     * Parameters must be:
     * -- peer_id: the id of the peer that addresses the request
     * -- info_hash: the SHA1 hash of the torrent the peer is sharing and requesting
     * -- port: the port the peer is listening on
     * -- ip: (optional) the ip address of the peer
     * -- updated: total amount already uploaded by the peer
     * -- downloaded: total amount already downloaded by the peer
     * -- left: number of bytes left to complete download. O means peer is a seed

     * @param resp Response message that will be constructed and returned.
     * The response is a bencoded dictionary containing the list of all peers currently sharing the file given in parameter
     *
     * @throws IOException
     */
    public void process(Request req, Response resp) throws IOException {
        HashMap hm = this.parseURI(req.getURI());
        byte[] answer = null;
        if(hm != null){
            TreeMap param = new TreeMap(hm);

            if (param.get("ip") == null) {
                param.put("ip", req.getInetAddress().toString().substring(1));
            }

            List peers = new ArrayList();
            int message = this.processPeerList(param, peers);
            answer = this.createAnswer(message, peers);
        }else
            answer = this.createAnswer(10, null);
        resp.set("Content-Type", "text/plain");
        resp.setDate("Date", System.currentTimeMillis());
        resp.set("Server", (String) Constants.get("servername"));
        OutputStream out = resp.getOutputStream();
        out.write(answer);
        out.close();
    }

    /**
     * Method called to parse the Request URI and retrieve the parameters in it
     *
     * @param uri The URI to be parsed
     * @return A HashMap containing the parameters names (keys) and corresponding values
     * @throws UnsupportedEncodingException
     */
    private HashMap parseURI(String uri) throws UnsupportedEncodingException{
        String[] temp = uri.split("[?]");
        //String decURI = URLDecoder.decode(uri, Constants.BYTE_ENCODING);
        HashMap<String, String> params = null;
        if(temp.length >= 2){
            String[] param = temp[1].split("[&]");
            params = new HashMap<String,String>(param.length);
            for(int i = 0; i < param.length; i++){
                String[] splitParam = param[i].split("[=]");
                if(splitParam.length == 1)
                    params.put(splitParam[0], "");
                else if(splitParam.length == 2)
                    params.put(splitParam[0], URLDecoder.decode(splitParam[1],
                            Constants.BYTE_ENCODING));
            }
            return params;
        }
        return null;
    }

    /**
     * Encode the answer that will be sent to the peer.
     * @param err Represent the status of the response. If different from 0, then
     * it means that an error occured and the response will be a message that
     * explains what goes wrong.
     * If equal to zero, then the answer a bencoded dictionary containing the peers
     * sharing the file, and contained in the List l
     * @param l The list of peers sharing the file
     * @return byte[] The response bytes
     */
    private byte[] createAnswer(final int err, final List l) {
        TreeMap ans = new TreeMap();
        try {
            switch (err) {
            case 0:
                ans.put("interval", new Integer(300));
                ArrayList peers = new ArrayList(l.size());
                for (Iterator it = l.iterator(); it.hasNext(); ) {
                    Element e = (Element) it.next();
                    TreeMap peer = new TreeMap();
                    peer.put("ip", e.getChild("ip").getText());
                    String s = URLDecoder.decode(Utils.byteStringToByteArray(e.getChild("id").getText()),
                                                Constants.BYTE_ENCODING);
                    peer.put("peer_id", s);
                    peer.put("port", new Integer(e.getChild("port").getText()));

                    peers.add(peer);
                }
                ans.put("peers", peers);
                return BEncoder.encode(ans);
            default:
                ans.put("failure reason", this.MESSAGE[err - 1]);
                return BEncoder.encode(ans);
            }
        } catch (Exception e) {

        }
        return null;
    }

    /**
     * Search if the torrent requested is registered to this tracker
     * @param root Root element of the XML document representing the torrents database
     * @param hash The SHA1 hash of the requested torrent, i.e. the torrent id
     * @return boolean true if the torrent is not registered, false otherwise
     */
    private boolean isNewTorrent(Element root, String hash) {
        for (Iterator hashIt = root.getDescendants(new ElementFilter("hash"));
                               hashIt.hasNext(); ) {
            if (((Element) hashIt.next()).getText().matches(hash)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cleans the peer list. It removes all peers that haven't been updated from more
     * than 5 minutes. It's necessary since these peers may not be active anymore.
     * @param root The root of the XML document that represents the peers currently
     * sharing a file and announced to this tracker
     */
    private void cleanPeerList(Element root) {
        for (Iterator it = root.getDescendants(new ElementFilter("peer"));
                           it.hasNext(); ) {
            Element e = (Element) it.next();
            long l = new Long(e.getChildText("updated")).longValue();
            if (System.currentTimeMillis() - l > 300000) {
                it.remove();
            }
        }
    }

    /**
     * Register or update the remote peer in the local peer database, represented by a XML document
     * @param root The root element of the XML document representing the peer database
     * @param id The id of the peer
     * @param ip The ip address of the peer
     * @param port The listening port of the peer
     * @param hash The file(s) that the peer is sharing
     */
    private void registerPeer(Element root, String id, String ip, String port,
                              String hash) {
        try {
            if (id == null || ip == null || port == null || hash == null) {
                return;
            }
            Element peer = null;
            for (Iterator peerIt = root.getDescendants(new ElementFilter("peer"));
                                   peerIt.hasNext(); ) {
                peer = (Element) peerIt.next();
                if (peer.getChild("id").getText().matches(id) ||
                        (peer.getChild("ip").getText() +
                         peer.getChild("port").getText()).matches(ip+port)) {
                    break;
                }
                peer = null;
            }
            if (peer != null) {
                peer.getChild("id").setText(id);
                peer.getChild("ip").setText(ip);
                peer.getChild("port").setText(port);
                for (Iterator it = peer.getDescendants(new ElementFilter(
                        "torrentid")); it.hasNext(); ) {
                    if (((Element) it.next()).getText().matches(hash)) {
                        return;
                    }
                }
                Element torrent = new Element("torrentid");
                torrent.setText(hash);
                peer.getChild("torrents").addContent(torrent);
                peer.setAttribute("updated",
                                  new Long(System.currentTimeMillis()).toString());
                peer.getChild("updated").setText(new Long(System.
                        currentTimeMillis()).toString());
            } else {
                Element newPeer = new Element("peer");
                Element idEl = new Element("id");
                idEl.setText(id);
                newPeer.addContent(idEl);
                Element ipEl = new Element("ip");
                ipEl.setText(ip);
                newPeer.addContent(ipEl);
                Element portEl = new Element("port");
                portEl.setText(port);
                Element hashEl = new Element("torrentid");
                hashEl.setText(hash);
                newPeer.addContent(portEl);
                Element torrents = new Element("torrents");
                torrents.addContent(hashEl);
                newPeer.addContent(torrents);
                Element updated = new Element("updated");
                updated.setText(new Long(System.currentTimeMillis()).
                                toString());
                newPeer.addContent(updated);
                root.addContent(newPeer);
            }
        } catch (Exception e) {

        }
    }

    /**
     * Returns the list of the peer currently sharing a given torrent
     * @param root Root element of the XML document representing the peer database
     * @param hash Id of the requested file
     * @param peerid Id of the peer
     * @return List List of all peers sharing the file
     */
    private List peerSharing(Element root, String hash, String peerid) {
        List peers = new ArrayList();
        for (Iterator hashIt = root.getDescendants(new ElementFilter(
                "torrentid")); hashIt.hasNext(); ) {
            Element id = (Element) hashIt.next();
            if (id.getText().matches(hash)) {
                Element peer = id.getParentElement().getParentElement();
                if (!peer.getChild("id").getText().matches(peerid)) {
                    peers.add(peer);
                }
            }
        }
        return peers;
    }

    /**
     * Look if the given torrent is already registered on this tracker
     * @param id The file id
     * @return boolean true if the torrent is registered, false otherwise
     */
    private boolean torrentExists(String id) {
        File torrentXML = new File((String) Constants.get("torrentXML"));
        if (torrentXML.exists() || torrentXML.length() > 0) {
            try {
                SAXBuilder sb = new SAXBuilder();
                Document d = sb.build(torrentXML);
                Element root = d.getRootElement();
                if (root != null) {
                    if (!this.isNewTorrent(root, id)) {
                        return true;
                    }
                }
            } catch (Exception fnfe) {

            }
        }
        return false;
    }

    /**
     * Process the peer list according to the given parameters. Processing the list
     * includes:
     * - cleaning the too old peers
     * - retrieving peers currently sharing the file
     * - register or update the peer in the XML document
     * @param param Parameters needed to process the peers list. Parameters must be:
     * info_hash, peer_id, port, ip
     * @param peers List where all peers sharing the file will be returned.
     * @return int Error message representing the status of the response. 0 means no
     * problem occured. Otherwise, the number represents the error that occured.
     */
    public int processPeerList(Map param, List peers) {
        SAXBuilder sb = new SAXBuilder();
        try {
            String hashRaw = (String) param.get("info_hash");
            if (hashRaw == null) {
                return this.MISSING_FILEID;
            }
            String peeridRaw = (String) param.get("peer_id");
            if (peeridRaw == null) {
                if(param.get("no_peer_id") != null)
                    peeridRaw = (String)param.get("no_peer_id");
                else
                    return this.MISSING_PEERID;
            }
            String port = (String) param.get("port");
            if (port == null) {
                return this.MISSING_PORT;
            }

            String hash = Utils.byteArrayToByteString(hashRaw.getBytes(
                    Constants.BYTE_ENCODING));
            String peerid = Utils.byteArrayToByteString(peeridRaw.getBytes(
                    Constants.BYTE_ENCODING));
            if (!this.torrentExists(hash)) {
                return this.UNKNOWN_TORRENT;
            }
            File xmlPeer = new File((String) Constants.get("peerXML"));
            if (!xmlPeer.exists() || xmlPeer.length() == 0) {
                xmlPeer.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(xmlPeer);
                fw.write(
                        "<?xml version=\"1.0\"?>\r\n<peerlist>\r\n</peerlist>");
                fw.flush();
                fw.close();
            }
            this.peerList = sb.build(xmlPeer);
            Element root = this.peerList.getRootElement();
            this.cleanPeerList(root);
            if (root != null) {
                peers.addAll(this.peerSharing(root, hash, peerid));
                this.registerPeer(root, peerid,
                                  (String) param.get("ip"),
                                  port,
                                  hash);
                XMLOutputter xo = new XMLOutputter(Format.getPrettyFormat());
                FileOutputStream fos = new FileOutputStream(xmlPeer);
                xo.output(root, fos);
                fos.flush();
                fos.close();

                return this.DONE;
            }

        } catch (JDOMException je) {
            System.err.println("File is not a xml valid file");
        } catch (IOException ioe) {
        } catch (Exception e) {}
        return this.INTERNAL_ERROR;
    }
}
