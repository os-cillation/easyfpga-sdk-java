/*
 *  This file is part of easyFPGA.
 *  Copyright 2013-2015 os-cillation GmbH
 *
 *  easyFPGA is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  easyFPGA is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with easyFPGA.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package easyfpga.communicator;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles the generation and release of frame IDs
 */
class IDGenerator {

    private ConcurrentLinkedQueue<Integer> idQueue;

    public IDGenerator() {
        /* initialize the queue */
        idQueue = new ConcurrentLinkedQueue<Integer>();
        for (int i = 1; i < 255; i++) {
            idQueue.add(i);
        }
    }

    /**
     * Get a free ID and lock it. Blocks until a free ID is available.
     *
     * @return Package ID
     * @throws InterruptedException
     */
    public int getFreeID() {
        Integer id = idQueue.poll();
        if (id == null) {
            return -1;
        }
        else {
            return id;
        }
    }

    /**
     * Release an ID
     *
     * @param id to be released (1 .. 255)
     */
    public void releaseID(int id) {

        /* check ID parameter */
        if (id < 1 || id > 255) {
            if (id == 0) throw new IllegalArgumentException("ID #0 cannot be released");
            else throw new IllegalArgumentException("Trying to free invalid ID");
        }

        /* enqueue if not already contained by queue */
        if (!idQueue.contains((Integer) id)) {
            idQueue.add(id);
        }
        else {
            System.err.println("Warning: Tried to free a free packet ID");
        }
    }

    @Override
    public synchronized String toString() {
        ArrayList<Integer> freeIDs = new ArrayList<Integer>();
        for (int i = 1; i < 255; i++) {
            freeIDs.add(i);
        }
        freeIDs.removeAll(idQueue);

        return "IDs taken:" + freeIDs.toString();
    }
}
