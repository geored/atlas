/*******************************************************************************
 * Copyright (C) 2013 John Casey.
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
 ******************************************************************************/
package org.commonjava.maven.atlas.spi.neo4j.io;

import static org.apache.commons.lang.StringUtils.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ExtensionRelationship;
import org.apache.maven.graph.effective.rel.ParentRelationship;
import org.apache.maven.graph.effective.rel.PluginDependencyRelationship;
import org.apache.maven.graph.effective.rel.PluginRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.maven.atlas.spi.neo4j.effective.GraphRelType;
import org.commonjava.maven.atlas.spi.neo4j.effective.NodeType;
import org.commonjava.util.logging.Logger;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;

public final class Conversions
{

    private static final Logger LOGGER = new Logger( Conversions.class );

    public static final String RELATIONSHIP_ID = "relationship_id";

    public static final String GROUP_ID = "groupId";

    public static final String ARTIFACT_ID = "artifactId";

    public static final String VERSION = "version";

    public static final String GAV = "gav";

    public static final String INDEX = "index";

    public static final String IS_REPORTING_PLUGIN = "reporting";

    public static final String IS_MANAGED = "managed";

    public static final String PLUGIN_GROUP_ID = "plugin_groupId";

    public static final String PLUGIN_ARTIFACT_ID = "plugin_artifactId";

    public static final String TYPE = "type";

    public static final String CLASSIFIER = "classifier";

    public static final String SCOPE = "scope";

    public static final String OPTIONAL = "optional";

    public static final String EXCLUDES = "excludes";

    public static final String CYCLE_ID = "cycle_id";

    public static final String CYCLE_RELATIONSHIPS = "relationship_participants";

    public static final String CYCLE_PROJECTS = "project_participants";

    private static final String METADATA_PREFIX = "_metadata_";

    public static final String NODE_TYPE = "_node_type";

    public static final String CYCLE_MEMBERSHIP = "cycle_membership";

    public static final String VARIABLE = "_variable";

    public static final String CONNECTED = "_connected";

    public static final String SELECTED_FOR = "_selected_for";

    public static final String DESELECTED_FOR = "_deselected_for";

    public static final String CLONE_OF = "_clone_of";

    private Conversions()
    {
    }

    public static List<ProjectVersionRef> convertToProjects( final Iterable<Node> nodes )
    {
        final List<ProjectVersionRef> refs = new ArrayList<ProjectVersionRef>();
        for ( final Node node : nodes )
        {
            if ( node.getId() == 0 )
            {
                continue;
            }

            if ( !Conversions.isType( node, NodeType.PROJECT ) )
            {
                continue;
            }

            refs.add( Conversions.toProjectVersionRef( node ) );
        }

        return refs;
    }

    public static List<ProjectRelationship<?>> convertToRelationships( final Iterable<Relationship> relationships )
    {
        final List<ProjectRelationship<?>> rels = new ArrayList<ProjectRelationship<?>>();
        for ( final Relationship relationship : relationships )
        {
            final ProjectRelationship<?> rel = Conversions.toProjectRelationship( relationship );
            if ( rel != null )
            {
                rels.add( rel );
            }
        }

        return rels;
    }

    public static void toNodeProperties( final ProjectVersionRef ref, final Node node, final boolean connected )
    {
        final String g = ref.getGroupId();
        final String a = ref.getArtifactId();
        final String v = ref.getVersionString();

        if ( empty( g ) || empty( a ) || empty( v ) )
        {
            throw new IllegalArgumentException( String.format( "GAV cannot contain nulls: %s:%s:%s", g, a, v ) );
        }

        node.setProperty( NODE_TYPE, NodeType.PROJECT.name() );
        node.setProperty( ARTIFACT_ID, a );
        node.setProperty( GROUP_ID, g );
        node.setProperty( VERSION, v );
        node.setProperty( GAV, ref.toString() );

        if ( ref.isCompound() )
        {
            LOGGER.debug( "Marking: %s as variable.", ref );
            node.setProperty( VARIABLE, true );
        }

        markConnected( node, connected );
    }

    public static boolean isAtlasType( final Relationship rel )
    {
        return GraphRelType.valueOf( rel.getType()
                                        .name() )
                           .isAtlasRelationship();
    }

    public static boolean isType( final Node node, final NodeType type )
    {
        final String nt = getStringProperty( NODE_TYPE, node );
        return nt != null && type == NodeType.valueOf( nt );
    }

    public static ProjectVersionRef toProjectVersionRef( final Node node )
    {
        if ( node == null )
        {
            return null;
        }

        if ( !isType( node, NodeType.PROJECT ) )
        {
            throw new IllegalArgumentException( "Node " + node.getId() + " is not a project reference." );
        }

        final String g = getStringProperty( GROUP_ID, node );
        final String a = getStringProperty( ARTIFACT_ID, node );
        final String v = getStringProperty( VERSION, node );

        if ( empty( g ) || empty( a ) || empty( v ) )
        {
            throw new IllegalArgumentException( String.format( "GAV cannot contain nulls: %s:%s:%s", g, a, v ) );
        }

        return new ProjectVersionRef( g, a, v );
    }

    private static boolean empty( final String val )
    {
        return val == null || val.trim()
                                 .length() < 1;
    }

    @SuppressWarnings( "incomplete-switch" )
    public static void toRelationshipProperties( final ProjectRelationship<?> rel, final Relationship relationship )
    {
        relationship.setProperty( INDEX, rel.getIndex() );
        switch ( rel.getType() )
        {
            case DEPENDENCY:
            {
                final DependencyRelationship specificRel = (DependencyRelationship) rel;
                toRelationshipProperties( (ArtifactRef) rel.getTarget(), relationship );
                relationship.setProperty( IS_MANAGED, specificRel.isManaged() );
                relationship.setProperty( SCOPE, specificRel.getScope()
                                                            .realName() );

                final Set<ProjectRef> excludes = specificRel.getExcludes();
                if ( excludes != null && !excludes.isEmpty() )
                {
                    final StringBuilder sb = new StringBuilder();
                    for ( final ProjectRef exclude : excludes )
                    {
                        if ( sb.length() > 0 )
                        {
                            sb.append( "," );
                        }

                        sb.append( exclude.getGroupId() )
                          .append( ":" )
                          .append( exclude.getArtifactId() );
                    }

                    relationship.setProperty( EXCLUDES, sb.toString() );
                }

                break;
            }
            case PLUGIN_DEP:
            {
                toRelationshipProperties( (ArtifactRef) rel.getTarget(), relationship );

                final PluginDependencyRelationship specificRel = (PluginDependencyRelationship) rel;

                final ProjectRef plugin = specificRel.getPlugin();
                relationship.setProperty( PLUGIN_ARTIFACT_ID, plugin.getArtifactId() );
                relationship.setProperty( PLUGIN_GROUP_ID, plugin.getGroupId() );
                relationship.setProperty( IS_MANAGED, specificRel.isManaged() );

                break;
            }
            case PLUGIN:
            {
                final PluginRelationship specificRel = (PluginRelationship) rel;
                relationship.setProperty( IS_MANAGED, specificRel.isManaged() );
                relationship.setProperty( IS_REPORTING_PLUGIN, specificRel.isReporting() );

                break;
            }
        }
    }

    public static ProjectRelationship<?> toProjectRelationship( final Relationship rel )
    {
        if ( rel == null )
        {
            return null;
        }

        final GraphRelType mapper = GraphRelType.valueOf( rel.getType()
                                                             .name() );

        //        LOGGER.debug( "Converting relationship of type: %s (atlas type: %s)", mapper,
        //                                              mapper.atlasType() );

        if ( !mapper.isAtlasRelationship() )
        {
            return null;
        }

        if ( rel.getStartNode() == null || rel.getEndNode() == null || !isType( rel.getStartNode(), NodeType.PROJECT )
            || !isType( rel.getEndNode(), NodeType.PROJECT ) )
        {
            return null;
        }

        final ProjectVersionRef from = toProjectVersionRef( rel.getStartNode() );
        final ProjectVersionRef to = toProjectVersionRef( rel.getEndNode() );
        final int index = getIntegerProperty( INDEX, rel );

        ProjectRelationship<?> result = null;
        switch ( mapper.atlasType() )
        {
            case DEPENDENCY:
            {
                final ArtifactRef artifact = toArtifactRef( to, rel );
                final boolean managed = getBooleanProperty( IS_MANAGED, rel );
                final String scopeStr = getStringProperty( SCOPE, rel );
                final DependencyScope scope = DependencyScope.getScope( scopeStr );

                final String excludeStr = getStringProperty( EXCLUDES, rel );
                final Set<ProjectRef> excludes = new HashSet<ProjectRef>();
                if ( excludeStr != null )
                {
                    final String[] e = excludeStr.split( "\\s*,\\s*" );
                    for ( final String ex : e )
                    {
                        final String[] parts = ex.split( ":" );
                        if ( parts.length != 2 )
                        {
                            LOGGER.error( "In: %s -> %s skipping invalid exclude specification: '%s'", from, artifact,
                                          ex );
                        }
                        else
                        {
                            excludes.add( new ProjectRef( parts[0], parts[1] ) );
                        }
                    }
                }

                result =
                    new DependencyRelationship( from, artifact, scope, index, managed,
                                                excludes.toArray( new ProjectRef[] {} ) );
                break;
            }
            case PLUGIN_DEP:
            {
                final ArtifactRef artifact = toArtifactRef( to, rel );
                final String pa = getStringProperty( PLUGIN_ARTIFACT_ID, rel );
                final String pg = getStringProperty( PLUGIN_GROUP_ID, rel );
                final boolean managed = getBooleanProperty( IS_MANAGED, rel );

                result = new PluginDependencyRelationship( from, new ProjectRef( pg, pa ), artifact, index, managed );
                break;
            }
            case PLUGIN:
            {
                final boolean managed = getBooleanProperty( IS_MANAGED, rel );
                final boolean reporting = getBooleanProperty( IS_REPORTING_PLUGIN, rel );

                result = new PluginRelationship( from, to, index, managed, reporting );
                break;
            }
            case EXTENSION:
            {
                result = new ExtensionRelationship( from, to, index );
                break;
            }
            case PARENT:
            {
                result = new ParentRelationship( from, to );
                break;
            }
            default:
            {
            }
        }

        //        LOGGER.debug( "Returning project relationship: %s", result );
        return result;
    }

    public static String id( final ProjectRelationship<?> rel )
    {
        return DigestUtils.shaHex( rel.toString() );
    }

    private static ArtifactRef toArtifactRef( final ProjectVersionRef ref, final Relationship rel )
    {
        if ( ref == null )
        {
            return null;
        }

        final String type = getStringProperty( TYPE, rel );
        final String classifier = getStringProperty( CLASSIFIER, rel );
        final boolean optional = getBooleanProperty( OPTIONAL, rel );

        return new ArtifactRef( ref, type, classifier, optional );
    }

    private static void toRelationshipProperties( final ArtifactRef target, final Relationship relationship )
    {
        relationship.setProperty( OPTIONAL, target.isOptional() );
        relationship.setProperty( TYPE, target.getType() );
        if ( target.getClassifier() != null )
        {
            relationship.setProperty( CLASSIFIER, target.getClassifier() );
        }
    }

    public static String getStringProperty( final String prop, final PropertyContainer container )
    {
        if ( container.hasProperty( prop ) )
        {
            return (String) container.getProperty( prop );
        }
        return null;
    }

    public static Boolean getBooleanProperty( final String prop, final PropertyContainer container )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Boolean) container.getProperty( prop );
        }
        return null;
    }

    public static Boolean getBooleanProperty( final String prop, final PropertyContainer container,
                                              final Boolean defaultValue )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Boolean) container.getProperty( prop );
        }

        return defaultValue;
    }

    public static Integer getIntegerProperty( final String prop, final PropertyContainer container )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Integer) container.getProperty( prop );
        }
        return null;
    }

    public static void setMetadata( final String key, final String value, final PropertyContainer container )
    {
        container.setProperty( METADATA_PREFIX + key, value );
    }

    public static void setMetadata( final Map<String, String> metadata, final PropertyContainer container )
    {
        for ( final Map.Entry<String, String> entry : metadata.entrySet() )
        {
            container.setProperty( METADATA_PREFIX + entry.getKey(), entry.getValue() );
        }
    }

    public static Map<String, String> getMetadataMap( final PropertyContainer container )
    {
        final Iterable<String> keys = container.getPropertyKeys();
        final Map<String, String> md = new HashMap<String, String>();
        for ( final String key : keys )
        {
            if ( !key.startsWith( METADATA_PREFIX ) )
            {
                continue;
            }

            final String k = key.substring( METADATA_PREFIX.length() );
            final String value = getStringProperty( key, container );

            md.put( k, value );
        }

        return md.isEmpty() ? null : md;
    }

    public static String getMetadata( final String key, final PropertyContainer container )
    {
        return getStringProperty( METADATA_PREFIX + key, container );
    }

    public static void toNodeProperties( final String cycleId, final String rawCycleId,
                                         final Set<ProjectVersionRef> refs, final Node node )
    {
        node.setProperty( NODE_TYPE, NodeType.CYCLE.name() );
        node.setProperty( CYCLE_ID, cycleId );
        node.setProperty( CYCLE_RELATIONSHIPS, rawCycleId );
        node.setProperty( CYCLE_PROJECTS, join( refs, "," ) );
    }

    public static boolean isConnected( final Node node )
    {
        return getBooleanProperty( CONNECTED, node );
    }

    public static void markConnected( final Node node, final boolean connected )
    {
        node.setProperty( CONNECTED, connected );
    }

    public static void cloneRelationshipProperties( final Relationship from, final Relationship to )
    {
        final Iterable<String> keys = from.getPropertyKeys();
        for ( final String key : keys )
        {
            to.setProperty( key, from.getProperty( key ) );
        }

        to.setProperty( CLONE_OF, from.getId() );
    }

    public static boolean isCloneFor( final Relationship relationship, final Relationship original )
    {
        if ( relationship.hasProperty( CLONE_OF ) )
        {
            final long id = (Long) relationship.getProperty( CLONE_OF );
            return original.getId() == id;
        }

        return false;
    }

    public static void markSelectedFor( final Relationship relationship, final Node root )
    {
        LOGGER.debug( "Marking selected: %s for root: %s", getStringProperty( GAV, relationship.getEndNode() ),
                      getStringProperty( GAV, root ) );
        addToIdListing( root.getId(), SELECTED_FOR, relationship );
        removeFromIdListing( root.getId(), DESELECTED_FOR, relationship );
    }

    public static void markDeselectedFor( final Relationship relationship, final Node root )
    {
        LOGGER.debug( "Marking de-selected: %s for root: %s", getStringProperty( GAV, relationship.getEndNode() ),
                      getStringProperty( GAV, root ) );
        removeFromIdListing( root.getId(), SELECTED_FOR, relationship );
        addToIdListing( root.getId(), DESELECTED_FOR, relationship );
    }

    public static void removeSelectionAnnotationsFor( final Relationship relationship, final Node root )
    {
        LOGGER.debug( "%s: removing ALL selection annotations for: %s",
                      getStringProperty( GAV, relationship.getEndNode() ), getStringProperty( GAV, root ) );
        removeFromIdListing( root.getId(), SELECTED_FOR, relationship );
        removeFromIdListing( root.getId(), DESELECTED_FOR, relationship );
    }

    private static boolean idListingContains( final long target, final String property, final Relationship relationship )
    {
        final long[] ids = (long[]) relationship.getProperty( property, new long[] {} );

        Arrays.sort( ids );
        return Arrays.binarySearch( ids, target ) >= 0;
    }

    private static void addToIdListing( final long target, final String property, final Relationship relationship )
    {
        final Set<Long> ids = new HashSet<Long>();
        boolean contains = false;
        for ( final long id : (long[]) relationship.getProperty( property, new long[] {} ) )
        {
            if ( id == target )
            {
                contains = true;
            }

            ids.add( id );
        }

        if ( !contains )
        {
            ids.add( target );
            relationship.setProperty( property, ids.toArray( new Long[] {} ) );
        }
    }

    private static void removeFromIdListing( final long target, final String property, final Relationship relationship )
    {
        final Set<Long> ids = new HashSet<Long>();
        boolean changed = false;
        for ( final long id : (long[]) relationship.getProperty( property, new long[] {} ) )
        {
            if ( id == target )
            {
                LOGGER.debug( "Found id: %d in property: %s.", target, property );
                changed = true;
            }
            else
            {
                ids.add( id );
            }
        }

        if ( changed )
        {
            if ( ids.isEmpty() )
            {
                LOGGER.debug( "Removing empty property: %s from: %s", property, relationship );
                relationship.removeProperty( property );
            }
            else
            {
                LOGGER.debug( "Modifying property: %s from: %s", property, relationship );
                relationship.setProperty( property, ids.toArray( new Long[] {} ) );
            }
        }
    }

    public static boolean isDeselectedFor( final Node root, final Relationship relationship )
    {
        return idListingContains( root.getId(), DESELECTED_FOR, relationship );
    }

}
