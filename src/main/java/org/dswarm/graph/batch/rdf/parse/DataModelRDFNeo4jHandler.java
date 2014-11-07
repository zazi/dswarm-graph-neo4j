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
package org.dswarm.graph.batch.rdf.parse;

import org.dswarm.graph.DMPGraphException;
import org.dswarm.graph.batch.parse.DataModelNeo4jHandler;
import org.dswarm.graph.batch.rdf.RDFNeo4jProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author tgaengler
 */
public class DataModelRDFNeo4jHandler extends RDFNeo4jHandler {

	private static final Logger	LOG	= LoggerFactory.getLogger(DataModelRDFNeo4jHandler.class);

	public DataModelRDFNeo4jHandler(final RDFNeo4jProcessor processorArg) throws DMPGraphException {

		super(new DataModelNeo4jHandler(processorArg.getProcessor()), processorArg);
	}
}
