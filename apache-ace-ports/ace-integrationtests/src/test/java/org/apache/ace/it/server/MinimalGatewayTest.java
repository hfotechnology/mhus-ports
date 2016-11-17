/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.it.server;

import static junit.framework.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.OptionUtils.combine;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * 
 */
@RunWith( JUnit4TestRunner.class )
public class MinimalGatewayTest
{

    @Configuration
    public Option[] config()
    {
        System.setProperty( "java.protocol.handler.pkgs", "org.ops4j.pax.url" );

        return combine(
            options(
                felix().version( "3.2.2" )
            ),
            // TODO avoid to use hard coded version
            AssemblyConfigure.get( "ace-target", "mvn:org.apache.ace/org.apache.ace.target.devgateway/0.8.1-SNAPSHOT/zip/distribution" )

        );
    }

    @Inject
    BundleContext context;

    @Test
    public void resolve()
    {
        List<Bundle> notLoaded = new ArrayList<Bundle>();

        // just check that all have been resolved.
        for( Bundle bundle : context.getBundles() )
        {
            System.out.println( "+ " + bundle.getSymbolicName() );
            if( bundle.getState() != Bundle.ACTIVE )
            {
                System.err.println( "Bundle " + bundle.getLocation() + " has not been started." );
                notLoaded.add( bundle );
            }
        }
        if( notLoaded.size() > 0 )
        {
            fail( "Some bundles have not been started properly. See Log Messages for details." );
        }

    }
}
