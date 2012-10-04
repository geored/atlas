package org.apache.maven.graph.effective;

import java.util.Set;

import org.apache.maven.graph.effective.rel.ProjectRelationship;

public interface EProjectRelationshipCollection
{

    Set<ProjectRelationship<?>> getAllRelationships();

}