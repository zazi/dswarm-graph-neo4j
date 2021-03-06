/**
 * This file is part of d:swarm graph extension.
 *
 * d:swarm graph extension is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * d:swarm graph extension is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with d:swarm graph extension.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dswarm.graph.tx;

/**
 * @author tgaengler
 */
public interface TransactionHandler {

	void beginTx();

	void renewTx();

	void failTx();

	void succeedTx();

	/**
	 * Return true, if a new transaction was create; if an existing transaction is still open, this one will be returned.
	 *
	 * @return true, if a new transaction was create; if an existing transaction is still open, this one will be returned.
	 */
	boolean ensureRunningTx();

	boolean txIsClosed();
}
