/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 * OpenNMS(R) Licensing <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.topology.plugins.topo.bsm.operations;

import static org.opennms.features.topology.plugins.topo.bsm.GraphVertexToTopologyVertexConverter.createTopologyVertex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.features.topology.api.GraphContainer;
import org.opennms.features.topology.api.Operation;
import org.opennms.features.topology.api.OperationContext;
import org.opennms.features.topology.api.OperationContext.DisplayLocation;
import org.opennms.features.topology.api.support.VertexHopGraphProvider.DefaultVertexHopCriteria;
import org.opennms.features.topology.api.topo.Criteria;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.features.topology.plugins.topo.bsm.AbstractBusinessServiceVertex;
import org.opennms.features.topology.plugins.topo.bsm.BusinessServiceVertex;
import org.opennms.features.topology.plugins.topo.bsm.BusinessServiceVertexVisitor;
import org.opennms.features.topology.plugins.topo.bsm.IpServiceVertex;
import org.opennms.features.topology.plugins.topo.bsm.ReductionKeyVertex;
import org.opennms.features.topology.plugins.topo.bsm.simulate.SimulationAwareStateMachineFactory;
import org.opennms.netmgt.bsm.service.BusinessServiceManager;
import org.opennms.netmgt.bsm.service.BusinessServiceStateMachine;
import org.opennms.netmgt.bsm.service.model.BusinessService;
import org.opennms.netmgt.bsm.service.model.IpService;
import org.opennms.netmgt.bsm.service.model.graph.GraphVertex;
import org.opennms.netmgt.vaadin.core.InfoDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class ImpactAnalysisOperation implements Operation {
    private static final Logger LOG = LoggerFactory.getLogger(ImpactAnalysisOperation.class);

    private BusinessServiceManager businessServiceManager;

    @Override
    public void execute(List<VertexRef> targets, OperationContext operationContext) {
        final List<AbstractBusinessServiceVertex> vertices = getVertices(targets);
        final BusinessServiceStateMachine stateMachine = SimulationAwareStateMachineFactory.createStateMachine(businessServiceManager,
                operationContext.getGraphContainer().getCriteria());
        final Set<GraphVertex> graphVerticesToFocus = Sets.newHashSet();
        for (AbstractBusinessServiceVertex vertex : vertices) {
            vertex.accept(new BusinessServiceVertexVisitor<Void>() {
                @Override
                public Void visit(BusinessServiceVertex vertex) {
                    BusinessService businessService = businessServiceManager.getBusinessServiceById(vertex.getServiceId());
                    graphVerticesToFocus.addAll(stateMachine.calculateImpact(businessService));
                    return null;
                }

                @Override
                public Void visit(IpServiceVertex vertex) {
                    IpService ipService = businessServiceManager.getIpServiceById(vertex.getIpServiceId());
                    graphVerticesToFocus.addAll(stateMachine.calculateImpact(ipService));
                    return null;
                }

                @Override
                public Void visit(ReductionKeyVertex vertex) {
                    graphVerticesToFocus.addAll(stateMachine.calculateImpact(vertex.getReductionKey()));
                    return null;
                }
            });
        }
        LOG.info("Found {} business services impacted.", graphVerticesToFocus.size());

        if (graphVerticesToFocus.isEmpty()) {
            new InfoDialog("No result", "No vertices are impacted by the selected vertices.").open();
        } else {
            // add to focus
            GraphContainer container = operationContext.getGraphContainer();
            removeHopCriteria(container);
            graphVerticesToFocus.forEach(graphVertex -> container.addCriteria(
                    new DefaultVertexHopCriteria(createTopologyVertex(graphVertex))));
            // add the context vertex because it is missing in the root cause result
            container.addCriteria(new DefaultVertexHopCriteria(targets.get(0)));
            container.setSemanticZoomLevel(0);
            container.redoLayout();
        }
    }

    public static void removeHopCriteria(GraphContainer container) {
        Criteria[] currentCriteria = container.getCriteria();
        for (Criteria c : Arrays.copyOf(currentCriteria, currentCriteria.length)) {
            if (c instanceof DefaultVertexHopCriteria) {
                container.removeCriteria(c);
            }
        }
    }

    @Override
    public boolean display(List<VertexRef> targets, OperationContext operationContext) {
        if (operationContext.getDisplayLocation() == DisplayLocation.MENUBAR) {
            return false;
        }
        return getVertices(targets).size() > 0;
    }

    @Override
    public boolean enabled(final List<VertexRef> targets, final OperationContext operationContext) {
        return getVertices(targets).size() > 0;
    }

    public List<AbstractBusinessServiceVertex> getVertices(List<VertexRef> targets) {
        if (targets == null) {
            return Collections.emptyList();
        }
        return targets.stream()
                    .filter(v -> v instanceof AbstractBusinessServiceVertex)
                    .map(v -> (AbstractBusinessServiceVertex) v)
                    .collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return getClass().getCanonicalName();
    }

    public void setBusinessServiceManager(BusinessServiceManager businessServiceManager) {
        this.businessServiceManager = Objects.requireNonNull(businessServiceManager);
    }
}
