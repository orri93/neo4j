/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
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
package org.neo4j.cypher.internal.profiling;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.cypher.internal.v4_0.util.attribution.Id;
import org.neo4j.cypher.result.OperatorProfile;
import org.neo4j.cypher.result.QueryProfile;

public class ProfilingTracer implements QueryProfiler, QueryProfile
{
    public interface Clock
    {
        long nanoTime();

        Clock SYSTEM_TIMER = System::nanoTime;
    }

    private static final ProfilingTracerData ZERO = new ProfilingTracerData();

    private final Clock clock;
    private final KernelStatisticProvider statisticProvider;
    private final Map<Integer,ProfilingTracerData> data = new HashMap<>();

    public ProfilingTracer( KernelStatisticProvider statisticProvider )
    {
        this( Clock.SYSTEM_TIMER, statisticProvider );
    }

    ProfilingTracer( Clock clock, KernelStatisticProvider statisticProvider )
    {
        this.clock = clock;
        this.statisticProvider = statisticProvider;
    }

    @Override
    public OperatorProfile operatorProfile( int operatorId )
    {
        ProfilingTracerData value = data.get( operatorId );
        return value == null ? ZERO : value;
    }

    public long timeOf( Id query )
    {
        return operatorProfile( query.x() ).time();
    }

    public long dbHitsOf( Id query )
    {
        return operatorProfile( query.x() ).dbHits();
    }

    public long rowsOf( Id query )
    {
        return operatorProfile( query.x() ).rows();
    }

    @Override
    public OperatorProfileEvent executeOperator( Id queryId )
    {
        ProfilingTracerData queryData = this.data.get( queryId.x() );
        if ( queryData == null )
        {
            queryData = new ProfilingTracerData();
            this.data.put( queryId.x(), queryData );
        }
        return new ExecutionEvent( clock, statisticProvider, queryData );
    }

    private static class ExecutionEvent implements OperatorProfileEvent
    {
        private final long start;
        private final Clock clock;
        private final KernelStatisticProvider statisticProvider;
        private final ProfilingTracerData data;
        private long hitCount;
        private long rowCount;

        ExecutionEvent( Clock clock, KernelStatisticProvider statisticProvider, ProfilingTracerData data )
        {
            this.clock = clock;
            this.statisticProvider = statisticProvider;
            this.data = data;
            this.start = clock.nanoTime();
        }

        @Override
        public void close()
        {
            long executionTime = clock.nanoTime() - start;
            long pageCacheHits = statisticProvider.getPageCacheHits();
            long pageCacheFaults = statisticProvider.getPageCacheMisses();
            if ( data != null )
            {
                data.update( executionTime, hitCount, rowCount, pageCacheHits, pageCacheFaults );
            }
        }

        @Override
        public void dbHit()
        {
            hitCount++;
        }

        @Override
        public void row()
        {
            rowCount++;
        }

        @Override
        public void rows( int n )
        {
            rowCount += n;
        }
    }
}