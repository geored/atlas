/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.maven.atlas.graph.mutate;

import org.commonjava.maven.atlas.graph.ViewParams;
import org.commonjava.maven.atlas.graph.model.GraphPath;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.spi.RelationshipGraphConnection;

public class NoOpGraphMutator
    implements GraphMutator
{

    private static final long serialVersionUID = 1L;

    public static final NoOpGraphMutator INSTANCE = new NoOpGraphMutator();

    private NoOpGraphMutator()
    {
    }

    @Override
    public ProjectRelationship<?> selectFor( final ProjectRelationship<?> rel, final GraphPath<?> path,
                                             final RelationshipGraphConnection connection, final ViewParams params )
    {
        return rel;
    }

    @Override
    public GraphMutator getMutatorFor( final ProjectRelationship<?> rel, final RelationshipGraphConnection connection,
                                       final ViewParams params )
    {
        return this;
    }

    @Override
    public String getLongId()
    {
        return "NOP";
    }

    @Override
    public String getCondensedId()
    {
        return getLongId();
    }

    @Override
    public String toString()
    {
        return getLongId();
    }

    @Override
    public int hashCode()
    {
        return NoOpGraphMutator.class.hashCode() + 1;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        return true;
    }

}
