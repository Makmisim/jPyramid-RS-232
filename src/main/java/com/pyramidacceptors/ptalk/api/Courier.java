/*
 * Copyright (C) 2014 Pyramid Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.pyramidacceptors.ptalk.api;

import com.pyramidacceptors.ptalk.api.event.PTalkEvent;
import com.pyramidacceptors.ptalk.api.event.PTalkEventListener;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/**
 * Courier runs the communication loop. This only applies to serial protocols<br>
 * that use a polling logic. e.g. ccTalk or RS-232<br>
 * Courier is threadsafe.
 * <br>
 * @author <a href="mailto:cory@pyramidacceptors.com">Cory Todd</a>
 * @since 1.0.0.0
 */
final class Courier extends Thread {
    private final Logger logger = LoggerFactory.getLogger(Courier.class);

    private PyramidPort port;
    private AtomicBoolean _stopThread = new AtomicBoolean(false);
    private boolean _comOkay = true;
    
    // Socket to handle all data IO with slave
    private final ISocket socket;
    
    // Rate at which courier will poll slave
    private final int pollRate;
    
    // EventListner list - threadsafe
    private final CopyOnWriteArrayList<PTalkEventListener> listeners;
    
    /**
     * Create a new Courier instance<br>
     * <br>
     * @param port to deliver on and listen to
     * @param pollRate delay between polls
     * @param socket type of packet that will be handled
     */
    Courier(PyramidPort port, int pollRate, ISocket socket) {
        this.port = port;
        this.pollRate = pollRate;
        this.listeners = new CopyOnWriteArrayList<>();        
        this.socket = socket;
    }
    
    /**     
     * @return true if the comms are operating properly. The flag may be<br>
     * set to false under the following conditions:
     *   SerialPort disconnected: Device unreachable
     *   Unit stop responding to polls - this is common in RS-232 during <br>
     *     validation. Consider debouncing this value. The logs
     *     will report timing out during validation. This is normal.      
     */
    boolean getCommsOkay() {
        return this._comOkay;
    }

    /**
     * Subscribe to events generated by this instance<br>
     * <br>
     * @param l PyramidSerialEventListener
     */
    public void addChangeListener(PTalkEventListener l) {
      this.listeners.add(l);
    }
    
    /**
     * Remove all subscriptions to this event.<br>
     * <br>
     */
    public void removeChangeListeners() {
      this.listeners.clear();
    }

    /**
     * Remove subscription to events generate by this instance<br>
     * <br>
     * @param l PyramidSerialEventListener
     */
    public void removeChangeListener(PTalkEventListener l) {
      this.listeners.remove(l);
    }

    // Event firing method.  Called internally by other class methods.
    private void fireChangeEvent(PTalkEvent e) {
        for (PTalkEventListener l : listeners) {
         l.changeEventReceived(e);
      }
    }
        
    /**
     * Stop the execution and null out reference objects
     */
    protected void stopThread() {
        this._stopThread.set(true);
        port = null;
    }
    
    /**
     * Start the courier thread. Poll in intervals determined by the poll<br>
     * rate passed to this instance's constructor.
     */
    @Override
    public void run() {
        
        // Loop until we receive client calls the stop thread method
        PTalkEvent e;
        while(!_stopThread.get()) {
            
            try {
    
                // Generate command and send to slave
                port.write(socket.generateCommand());
                
                // Collect the response
                e = socket.parseResponse(port.readBytes(
                        socket.getMaxPacketRespSize()));
               
                // Notify any listeners that we have data
                fireChangeEvent(e);                      
                
                // Wait for pollRate milliseconds before looping through again
                sleep(pollRate);
                       
            } catch (SerialPortException ex) {
                logger.error(ex.getMessage());
                _comOkay = false;
            } catch (SerialPortTimeoutException ex1) {
                logger.error("SendBytes timed out. ({0} ms)", APIConstants.COMM_TIMEOUT);
                _comOkay = false;
            }
        }
        
    }   
    
    /**
     * Sleep for d milliseconds<br>
     * <br>
     * @param d sleep time
     */
    private static void sleep(int d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException ex) {
            LoggerFactory.getLogger("CourierSleeper")
                    .error("Sleep interrupted: {}", ex);
        }     
    }
}
