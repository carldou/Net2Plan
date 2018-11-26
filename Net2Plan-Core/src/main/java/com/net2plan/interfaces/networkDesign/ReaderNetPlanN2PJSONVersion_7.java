package com.net2plan.interfaces.networkDesign;

import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import com.net2plan.internal.ErrorHandling;
import com.net2plan.internal.UnmodifiablePoint2D;
import com.net2plan.libraries.ProfileUtils;
import com.net2plan.libraries.TrafficSeries;
import com.net2plan.utils.*;
import com.shc.easyjson.JSONArray;
import com.shc.easyjson.JSONObject;
import org.apache.commons.lang3.mutable.MutableLong;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.awt.geom.Point2D;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ReaderNetPlanN2PJSONVersion_7 implements IReaderNetPlan_JSON
{

    private boolean hasAlreadyReadOneLayer;
    private XMLStreamReader2 xmlStreamReader;
    private SortedMap<Route,List<Long>> backupRouteIdsMap;
    private SortedMap<Long , List<Triple<Node,URL,Double>>> nodeAndLayerToIconURLMap;
    private SortedSet<Demand> newNpDemandsWithRoutingTypeNotDefined = new TreeSet<>();

    @Override
    public void createFromJSON(NetPlan netPlan, JSONObject json)
    {
        final long nextElementId = Long.parseLong(json.get("nextElementId").getValue());
        final String description = json.get("description").getValue();
        final String name = json.get("name").getValue();
        final JSONArray tags = json.get("tags").getValue();
        final JSONArray attributes = json.get("attributes").getValue();
        final JSONArray planningDomains = json.get("planningDomains").getValue();
        List<String> cache_definedPlotNodeLayouts = StringUtils.toList(StringUtils.split(json.get("cache_definedPlotNodeLayouts").getValue()));
        String currentPlot = json.get("currentPlotNodeLayout").getValue();
        cache_definedPlotNodeLayouts.stream().forEach(plot -> netPlan.addPlotNodeLayout(plot));
        netPlan.setPlotNodeLayoutCurrentlyActive(currentPlot);


        netPlan.nextElementId = new MutableLong(nextElementId);
        netPlan.setDescription(description);
        netPlan.setName(name);
        planningDomains.stream().forEach(pd -> netPlan.addGlobalPlanningDomain(pd.getValue()));
        tags.stream().forEach(tag -> netPlan.addTag(tag.getValue()));
        attributes.stream().forEach(
                att ->
                {
                    JSONObject attribute = att.getValue();
                    netPlan.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                }
        );

        JSONArray layers = json.get("layers").getValue();
        layers.stream().forEach(layer ->
        {
            JSONObject layerJSON = layer.getValue();
            long layerId = Long.parseLong(layerJSON.get("id").getValue());
            String layerName = layerJSON.get("name").getValue();
            String layerDescription = layerJSON.get("description").getValue();
            String layerDemandTrafficUnits = layerJSON.get("demandTrafficUnitsName").getValue();
            String layerLinkCapacityUnits = layerJSON.get("linkCapacityUnitsName").getValue();
            boolean isDefaultLayer = Boolean.parseBoolean(layerJSON.get("isDefaultLayer").getValue());
            URL layerIconURL = null;
            try {
                layerIconURL = (layerJSON.get("defaultNodeIconURL") == null) ? null : new URL(layerJSON.get("defaultNodeIconURL").getValue());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            JSONArray layerTags = layerJSON.get("tags").getValue();
            JSONArray layerAttributes = layerJSON.get("attributes").getValue();
            NetworkLayer newLayer;

            if (!hasAlreadyReadOneLayer)
            {
                if (netPlan.layers.size() != 1) throw new RuntimeException ("Bad");
                if (netPlan.layers.get (0).id != layerId)
                {
                    // the Id of first layer is different => create a new one and remove the existing
                    newLayer = netPlan.addLayer(layerId , layerName, layerDescription, layerLinkCapacityUnits, layerDemandTrafficUnits,  layerIconURL , null);
                    netPlan.removeNetworkLayer(netPlan.layers.get (0));
                }
                else
                {
                    newLayer = netPlan.layers.get (0); // it already has the right Id
                    newLayer.demandTrafficUnitsName = layerDemandTrafficUnits;
                    newLayer.description = layerDescription;
                    newLayer.name = layerName;
                    newLayer.linkCapacityUnitsName= layerLinkCapacityUnits;
                }
                hasAlreadyReadOneLayer = true;
            }
            else
            {
                newLayer = netPlan.addLayer(layerId , layerName, layerDescription, layerLinkCapacityUnits, layerDemandTrafficUnits,  layerIconURL , null);
            }

            layerTags.stream().forEach(tag -> newLayer.addTag(tag.getValue()));
            layerAttributes.stream().forEach(
                    att ->
                    {
                        JSONObject attribute = att.getValue();
                        newLayer.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                    }
            );

            if(isDefaultLayer)
                netPlan.setNetworkLayerDefault(newLayer);


        });

        JSONArray nodes = json.get("nodes").getValue();
        nodes.stream().forEach(
                node ->
                {
                    JSONObject nodeJSON = node.getValue();
                    Long nodeId = Long.parseLong(nodeJSON.get("id").getValue());
                    String nodeName = nodeJSON.get("name").getValue();
                    double xCoord = Double.parseDouble(nodeJSON.get("xCoord").getValue());
                    double yCoord = Double.parseDouble(nodeJSON.get("yCoord").getValue());

                    Node newNode = netPlan.addNode(nodeId , xCoord, yCoord, nodeName, null);

                    double nodePopulation = Double.parseDouble(nodeJSON.get("population").getValue());
                    String nodeDescription = nodeJSON.get("description").getValue();
                    boolean nodeIsUp = Boolean.parseBoolean(nodeJSON.get("isUp").getValue());
                    JSONArray nodePlanningDomains = json.get("planningDomains").getValue();
                    nodePlanningDomains.stream().forEach(pd -> newNode.addToPlanningDomain(pd.getValue()));
                    newNode.setPopulation(nodePopulation);
                    newNode.setDescription(nodeDescription);
                    newNode.setFailureState(nodeIsUp);

                    JSONArray nodeIconsJSON = nodeJSON.get("nodeIcons").getValue();
                    nodeIconsJSON.stream().forEach(
                            nodeIcon ->
                    {
                        JSONObject nodeIconJSON = nodeIcon.getValue();
                        long layerId = Long.parseLong(nodeIconJSON.get("layerId").getValue());
                        URL iconURL = null;
                        try {
                            iconURL = new URL(nodeIconJSON.get("nodeIconURLLayer").getValue());
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                        double iconRelativeSize = Double.parseDouble(nodeIconJSON.get("nodeIconRelativeSizeLayer").getValue());
                        NetworkLayer nl = netPlan.getNetworkLayerFromId(layerId);
                        newNode.setUrlNodeIcon(nl,iconURL, iconRelativeSize);

                    });

                    JSONArray nodeTags = nodeJSON.get("tags").getValue();
                    JSONArray nodeAttributes = nodeJSON.get("attributes").getValue();
                    nodeTags.stream().forEach(tag -> newNode.addTag(tag.getValue()));
                    nodeAttributes.stream().forEach(att ->
                            {
                                JSONObject attribute = att.getValue();
                                newNode.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                            });


                }
        );

        JSONArray resources = json.get("resources").getValue();
        resources.stream().forEach(res ->
        {
            JSONObject resource = res.getValue();
            long resourceId = Long.parseLong(resource.get("id").getValue());
            String resourceName = resource.get("name").getValue();
            String resourceType = resource.get("type").getValue();
            long hostNodeId = Long.parseLong(resource.get("hostNodeId").getValue());
            double resourceProcessingTime = Double.parseDouble(resource.get("processingTimeToTraversingTrafficInMs").getValue());
            String resourceUnits = resource.get("capacityMeasurementUnits").getValue();
            double resourceCapacity = Double.parseDouble(resource.get("capacity").getValue());

            JSONArray resourceBaseRes = resource.get("baseResourceAndOccupiedCapacitiesMap").getValue();
            Map<Resource, Double> baseResourceAndOccupiedCapacitiesMap = new LinkedHashMap<>();
            resourceBaseRes.stream().forEach(baseRes ->
            {
                JSONObject baseJSON = baseRes.getValue();
                baseResourceAndOccupiedCapacitiesMap.put(netPlan.getResourceFromId(baseJSON.get("id").getValue()), baseJSON.get("capacity").getValue());
            });
            Node hostNode = netPlan.getNodeFromId(hostNodeId);
            Optional<Node> hostNodeOpt = (hostNode == null) ? Optional.empty() : Optional.of(hostNode);

            Resource newRes = netPlan.addResource(resourceId, resourceType, resourceName, hostNodeOpt, resourceCapacity, resourceUnits, baseResourceAndOccupiedCapacitiesMap, resourceProcessingTime, null );

            JSONArray resTags = resource.get("tags").getValue();
            JSONArray resAttributes = resource.get("attributes").getValue();
            resTags.stream().forEach(tag -> newRes.addTag(tag.getValue()));
            resAttributes.stream().forEach(att ->
            {
                JSONObject attribute = att.getValue();
                newRes.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
            });


        });

        layers.stream().forEach(layer ->
        {
            JSONObject layerJSON = layer.getValue();
            long layerId = Long.parseLong(layerJSON.get("id").getValue());
            NetworkLayer nl = netPlan.getNetworkLayerFromId(layerId);

            JSONArray links = layerJSON.get("links").getValue();
            links.stream().forEach(link ->
            {
                JSONObject linkJSON = link.getValue();
                long linkId = Long.parseLong(linkJSON.get("id").getValue());
                String linkDescription = linkJSON.get("description").getValue();
                long originNodeId = Long.parseLong(linkJSON.get("originNodeId").getValue());
                long destinationNodeId = Long.parseLong(linkJSON.get("destinationNodeId").getValue());
                double linkLength = Double.parseDouble(linkJSON.get("lengthInKm").getValue());
                double linkCapacity = Double.parseDouble(linkJSON.get("capacity").getValue());
                double linkPropagationSpeed = Double.parseDouble(linkJSON.get("propagationSpeedInKmPerSecond").getValue());
                boolean linkIsUp = Boolean.parseBoolean(linkJSON.get("isUp").getValue());
                long linkBidirectionalPairId = Long.parseLong(linkJSON.get("bidirectionalPairId").getValue());

                Node originNode = netPlan.getNodeFromId(originNodeId);
                Node destinationNode = netPlan.getNodeFromId(destinationNodeId);
                Link bidirectionalPair = netPlan.getLinkFromId(linkBidirectionalPairId);

                Link newLink = netPlan.addLink(linkId, originNode, destinationNode, linkCapacity, linkLength, linkPropagationSpeed, null, nl);

                newLink.setDescription(linkDescription);
                newLink.setFailureState(linkIsUp);
                if(bidirectionalPair != null)
                    newLink.setBidirectionalPair(bidirectionalPair);

                JSONArray linkTags = linkJSON.get("tags").getValue();
                JSONArray linkAttributes = linkJSON.get("attributes").getValue();
                linkTags.stream().forEach(tag -> newLink.addTag(tag.getValue()));
                linkAttributes.stream().forEach(att ->
                {
                    JSONObject attribute = att.getValue();
                    newLink.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                });


            });

            JSONArray demands = layerJSON.get("demands").getValue();
            demands.stream().forEach(demand ->
                    {
                        JSONObject demandJSON = demand.getValue();
                        long demandId = Long.parseLong(demandJSON.get("id").getValue());
                        String demandDescription = demandJSON.get("description").getValue();
                        long ingressNodeId = Long.parseLong(demandJSON.get("ingressNodeId").getValue());
                        long egressNodeId = Long.parseLong(demandJSON.get("egressNodeId").getValue());
                        String demandRoutingType = demandJSON.get("routingType").getValue();
                        double demandOfferedTraffic = Double.parseDouble(demandJSON.get("offeredTraffic").getValue());
                        double demandOfferedTrafficGrowth = Double.parseDouble(demandJSON.get("offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth").getValue());
                        String demandQoSType = demandJSON.get("qosType").getValue();
                        long demandBidirectionalPairId = Long.parseLong(demandJSON.get("bidirectionalPairId").getValue());
                        double demandMaximumLatencyMs = Double.parseDouble(demandJSON.get("maximumAcceptableE2EWorstCaseLatencyInMs").getValue());
                        String demandIntendedRecovery = demandJSON.get("intendedRecoveryType").getValue();
                        String monitoredOrForecastedTrafficsValues = demandJSON.get("monitoredOrForecastedTraffics").getValue();
                        JSONArray demandServiceChainSequence = demandJSON.get("serviceChainResourceTypeOfSequence").getValue();

                        Node ingressNode = netPlan.getNodeFromId(ingressNodeId);
                        Node egressNode = netPlan.getNodeFromId(egressNodeId);
                        Demand bidirectionalDemand = netPlan.getDemandFromId(demandBidirectionalPairId);
                        TrafficSeries demandTrafficSeries = TrafficSeries.createFromStringList(StringUtils.readEscapedString_asStringList(monitoredOrForecastedTrafficsValues, new ArrayList<>()));

                        List<String> demandServiceChainSequenceList = demandServiceChainSequence.stream().map(val -> (String) val.getValue()).collect(Collectors.toList());

                        Constants.RoutingType type = Constants.RoutingType.SOURCE_ROUTING;
                        if (demandRoutingType.equals("Source Routing"))
                            type = Constants.RoutingType.SOURCE_ROUTING;
                        else if (demandRoutingType.equals("Hop-by-hop routing"))
                            type = Constants.RoutingType.HOP_BY_HOP_ROUTING;

                        Demand newDemand = netPlan.addDemand(demandId, ingressNode, egressNode, demandOfferedTraffic, type, null, nl);

                        newDemand.setDescription(demandDescription);
                        newDemand.setOfferedTrafficPerPeriodGrowthFactor(demandOfferedTrafficGrowth);
                        newDemand.setQoSType(demandQoSType);
                        if (bidirectionalDemand != null)
                            newDemand.setBidirectionalPair(bidirectionalDemand);

                        newDemand.setMaximumAcceptableE2EWorstCaseLatencyInMs(demandMaximumLatencyMs);
                        if(demandServiceChainSequence.size() > 0)
                            newDemand.setServiceChainSequenceOfTraversedResourceTypes(demandServiceChainSequenceList);
                        newDemand.setMonitoredOrForecastedOfferedTraffic(demandTrafficSeries);
                        newDemand.setIntendedRecoveryType(Demand.IntendedRecoveryType.valueOf(demandIntendedRecovery));

                        JSONArray demandAttributes = demandJSON.get("attributes").getValue();
                        JSONArray demandTags = demandJSON.get("tags").getValue();
                        demandTags.stream().forEach(tag -> newDemand.addTag(tag.getValue()));
                        demandAttributes.stream().forEach(att ->
                        {
                            JSONObject attribute = att.getValue();
                            newDemand.setAttribute(attribute.get("key").getValue(), (String) attribute.get("value").getValue());
                        });

                    });
                JSONArray multicastDemands = layerJSON.get("multicastDemands").getValue();
                multicastDemands.stream().forEach(md ->
                {
                    JSONObject mdJSON = md.getValue();
                    long mdId = Long.parseLong(mdJSON.get("id").getValue());
                    long mdIngressNodeId = Long.parseLong(mdJSON.get("ingressNodeId").getValue());
                    List<String> mdEgressNodesIds = StringUtils.toList(StringUtils.split(mdJSON.get("egressNodeIds").getValue()));
                    String mdDescription = mdJSON.get("description").getValue();
                    String mdQoSType = mdJSON.get("qosType").getValue();
                    double mdOfferedTraffic = Double.parseDouble(mdJSON.get("offeredTraffic").getValue());
                    double mdOfferedTrafficGrowthFactor = Double.parseDouble(mdJSON.get("offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth").getValue());
                    double mdMaxAcceptableLatency = Double.parseDouble(mdJSON.get("maximumAcceptableE2EWorstCaseLatencyInMs").getValue());
                    List<String> mdMonitoredTraffics = StringUtils.toList(StringUtils.split(mdJSON.get("monitoredOrForecastedTraffics").getValue()));

                    Node mdIngressNode = netPlan.getNodeFromId(mdIngressNodeId);
                    Set<Node> mdEgressNodes = mdEgressNodesIds.stream().map(id -> netPlan.getNodeFromId(Long.parseLong(id))).collect(Collectors.toSet());

                    MulticastDemand newMDemand = netPlan.addMulticastDemand(mdId, mdIngressNode, mdEgressNodes, mdOfferedTraffic, null, nl);

                    newMDemand.setDescription(mdDescription);
                    newMDemand.setQoSType(mdQoSType);
                    newMDemand.setOfferedTrafficPerPeriodGrowthFactor(mdOfferedTrafficGrowthFactor);
                    newMDemand.setMaximumAcceptableE2EWorstCaseLatencyInMs(mdMaxAcceptableLatency);
                    newMDemand.setMonitoredOrForecastedOfferedTraffic(TrafficSeries.createFromStringList(mdMonitoredTraffics));

                    JSONArray mdemandAttributes = mdJSON.get("attributes").getValue();
                    JSONArray mdemandTags = mdJSON.get("tags").getValue();
                    mdemandTags.stream().forEach(tag -> newMDemand.addTag(tag.getValue()));
                    mdemandAttributes.stream().forEach(att ->
                    {
                        JSONObject attribute = att.getValue();
                        newMDemand.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                    });

                });

                JSONArray multicastTrees = layerJSON.get("multicastTrees").getValue();
                multicastTrees.stream().forEach(mt ->
                {
                    JSONObject mtJSON = mt.getValue();
                    long mtId = mtJSON.get("id").getValue();
                    long mtDemandId = mtJSON.get("demandId").getValue();
                    String mtDescription = mtJSON.get("description").getValue();
                    List<String> mtCurrentSetLinks = StringUtils.toList(StringUtils.split(mtJSON.get("currentSetLinks").getValue()));
                    Set<Link> mtLinks = mtCurrentSetLinks.stream().map(id -> netPlan.getLinkFromId(Long.parseLong(id))).collect(Collectors.toSet());
                    double mtCarriedTraffic = mtJSON.get("carriedTrafficIfNotFailing").getValue();
                    double mtOccupiedLinkCapacity = mtJSON.get("occupiedLinkCapacityIfNotFailing").getValue();

                    MulticastDemand dem = netPlan.getMulticastDemandFromId(mtDemandId);

                    MulticastTree newTree = netPlan.addMulticastTree(mtId, dem, mtCarriedTraffic, mtOccupiedLinkCapacity, mtLinks, null);

                    newTree.setDescription(mtDescription);
                    JSONArray mtAttributes = mtJSON.get("attributes").getValue();
                    JSONArray mtTags = mtJSON.get("tags").getValue();
                    mtTags.stream().forEach(tag -> newTree.addTag(tag.getValue()));
                    mtAttributes.stream().forEach(att ->
                    {
                        JSONObject attribute = att.getValue();
                        newTree.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                    });

                });

                JSONObject sourceRoutingJSON = layerJSON.get("sourceRouting").getValue();
                backupRouteIdsMap = new TreeMap<>();
                JSONArray routes = sourceRoutingJSON.get("routes").getValue();
                routes.stream().forEach(route ->
                {
                    JSONObject routeJSON = route.getValue();
                    long routeId = Long.parseLong(routeJSON.get("id").getValue());
                    long routeDemandId = Long.parseLong(routeJSON.get("demandId").getValue());
                    String routeDescription = routeJSON.get("description").getValue();
                    double routeCarriedTraffic = Double.parseDouble(routeJSON.get("currentCarriedTrafficIfNotFailing").getValue());

                    List<String> routeCurrentPathIds = StringUtils.toList(StringUtils.split(routeJSON.get("currentPath").getValue()));
                    List<? extends NetworkElement> routeCurrentPath = routeCurrentPathIds.stream().map(id -> netPlan.getNetworkElement(Long.parseLong(id))).collect(Collectors.toList());

                    List<String> routeCurrentOccupations = StringUtils.toList(StringUtils.split(routeJSON.get("currentLinksAndResourcesOccupationIfNotFailing").getValue()));
                    List<Double> routeCurrentOccupations_double = routeCurrentOccupations.stream().mapToDouble(s -> Double.parseDouble(s)).boxed().collect(Collectors.toList());

                    List<String> routeBackupIds = StringUtils.toList(StringUtils.split(routeJSON.get("backupRoutes").getValue()));

                    long routeBidirectionalPairId = Long.parseLong(routeJSON.get("bidirectionalPairId").getValue());

                    Demand d = netPlan.getDemandFromId(routeDemandId);

                    Route newRoute = netPlan.addServiceChain(routeId, d, routeCarriedTraffic, routeCurrentOccupations_double, routeCurrentPath, null);
                    backupRouteIdsMap.put(newRoute, routeBackupIds.stream().map(id ->
                    {
                        try{
                            return Long.parseLong(id);
                        }
                        catch(Exception e)
                        {
                            return Integer.toUnsignedLong(-1);
                        }

                    }).collect(Collectors.toList()));

                    newRoute.setDescription(routeDescription);

                    Route bidirectionalPair = netPlan.getRouteFromId(routeBidirectionalPairId);
                    if(bidirectionalPair != null)
                        newRoute.setBidirectionalPair(bidirectionalPair);

                    JSONArray routeAttributes = routeJSON.get("attributes").getValue();
                    JSONArray routeTags = routeJSON.get("tags").getValue();
                    routeTags.stream().forEach(tag -> newRoute.addTag(tag.getValue()));
                    routeAttributes.stream().forEach(att ->
                    {
                        JSONObject attribute = att.getValue();
                        newRoute.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
                    });

                });

                for(NetworkLayer lay : netPlan.getNetworkLayers())
                {
                    for(Route r : netPlan.getRoutes(lay))
                    {
                        List<Long> backupRoutesIds = backupRouteIdsMap.get(r);
                        if(backupRoutesIds != null)
                        {
                            backupRoutesIds.stream().forEach(id ->
                            {
                                Route backup = netPlan.getRouteFromId(id);
                                if(backup != null)
                                    r.addBackupRoute(backup);
                            });
                        }

                    }
                }

                JSONObject hopByHopJSON = layerJSON.get("hopbyhopRouting").getValue();
                JSONArray fRules = hopByHopJSON.get("forwardingRules").getValue();
                fRules.stream().forEach(fRule ->
                {
                    JSONObject fRuleJSON = fRule.getValue();
                    long fRuleDemandId = Long.parseLong(fRuleJSON.get("demandId").getValue());
                    long fRuleLinkId = Long.parseLong(fRuleJSON.get("linkId").getValue());
                    double fRuleSplittingRatio = Double.parseDouble(fRuleJSON.get("splittingRatio").getValue());

                    Demand d = netPlan.getDemandFromId(fRuleDemandId);
                    Link l = netPlan.getLinkFromId(fRuleLinkId);

                    netPlan.setForwardingRule(d,l,fRuleSplittingRatio);
                });
            });



        JSONArray srgs = json.get("srgs").getValue();
        srgs.stream().forEach(srg ->
        {
            JSONObject srgJSON = srg.getValue();
            long srgId = Long.parseLong(srgJSON.get("id").getValue());
            String srgDescription = srgJSON.get("description").getValue();
            boolean srgIsDynamic = Boolean.parseBoolean(srgJSON.get("isDynamic").getValue());
            double meanTimeToFailInHours = Double.parseDouble(srgJSON.get("meanTimeToFailInHours").getValue());
            double meanTimeToRepairInHours = Double.parseDouble(srgJSON.get("meanTimeToRepairInHours").getValue());
            SharedRiskGroup newSRG;
            if (srgIsDynamic)
            {
                final String className = srgJSON.get("dynamicSrgClassName").getValue();
                final String configString = srgJSON.get("dynamicSrgConfigString").getValue();
                newSRG = netPlan.addSRGDynamic(srgId , meanTimeToFailInHours, meanTimeToRepairInHours, className , configString , null);
            }
            else
            {
                newSRG = netPlan.addSRG(srgId , meanTimeToFailInHours, meanTimeToRepairInHours, null);
                List<String> srgLinksIds = StringUtils.toList(StringUtils.split(srgJSON.get("links").getValue()));
                srgLinksIds.stream().forEach(id ->
                {
                    try{
                        long idd = Long.parseLong(id);
                        newSRG.addLink(netPlan.getLinkFromId(idd));
                    }catch (Exception e)
                    {

                    }
                });
                List<String> srgNodesIds = StringUtils.toList(StringUtils.split(srgJSON.get("nodes").getValue()));
                srgNodesIds.stream().forEach(id ->
                {
                    try{
                        long idd = Long.parseLong(id);
                        newSRG.addNode(netPlan.getNodeFromId(idd));
                    }catch(Exception e)
                    {

                    }
                });

            }

            newSRG.setDescription(srgDescription);

            JSONArray srgAttributes = srgJSON.get("attributes").getValue();
            JSONArray srgTags = srgJSON.get("tags").getValue();
            srgTags.stream().forEach(tag -> newSRG.addTag(tag.getValue()));
            srgAttributes.stream().forEach(att ->
            {
                JSONObject attribute = att.getValue();
                newSRG.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
            });
        });

        JSONArray demandLinkMappings = json.get("demandLinkMappings").getValue();
        demandLinkMappings.stream().forEach(demandLinkMap ->
        {
            JSONObject demandLinkMapJSON = demandLinkMap.getValue();
            JSONArray layerCouplingDemands = demandLinkMapJSON.get("layerCouplingDemand").getValue();
            layerCouplingDemands.stream().forEach(layerCoupling ->
            {
                JSONObject layerCouplingJSON = layerCoupling.getValue();
                long lowerLayerDemandId = Long.parseLong(layerCouplingJSON.get("lowerLayerDemandId").getValue());
                long upperLayerLinkId = Long.parseLong(layerCouplingJSON.get("upperLayerLinkId").getValue());
                Demand d = netPlan.getDemandFromId(lowerLayerDemandId);
                Link l = netPlan.getLinkFromId(upperLayerLinkId);
                d.coupleToUpperOrSameLayerLink(l);
            });

            JSONArray layerCouplingMulticastDemands = demandLinkMapJSON.get("layerCouplingMulticastDemand").getValue();
            layerCouplingMulticastDemands.stream().forEach(layerCouplingM ->
            {
                JSONObject layerCouplingMJSON = layerCouplingM.getValue();
                long lowerLayerDemandId = Long.parseLong(layerCouplingMJSON.get("lowerLayerDemandId").getValue());
                List<String> upperLayerLinksIds = StringUtils.toList(StringUtils.split(layerCouplingMJSON.get("upperLayerLinkIds").getValue()));
                MulticastDemand md = netPlan.getMulticastDemandFromId(lowerLayerDemandId);
                Set<Link> l = new LinkedHashSet<>();
                upperLayerLinksIds.stream().forEach(id ->
                {
                    try{
                        Long idd = Long.parseLong(id);
                        l.add(netPlan.getLinkFromId(idd));
                    }catch(Exception e)
                    {

                    }
                });

                md.couple(l);
            });
        });

        JSONArray layersDemandsCoupled = json.get("layersDemandsCoupled").getValue();
        layersDemandsCoupled.stream().forEach(layerDemandsCoupled ->
        {
            JSONArray layerCoupledJSON = layerDemandsCoupled.getValue();
            layerCoupledJSON.stream().forEach(obj ->
            {
                JSONObject this_layerCoupled = obj.getValue();
                long coupledDemandId = Long.parseLong(this_layerCoupled.get("layerDemandId").getValue());
                long bundleId = Long.parseLong(this_layerCoupled.get("layerLinkId").getValue());
                netPlan.getDemandFromId(coupledDemandId).coupleToUpperOrSameLayerLink(netPlan.getLinkFromId(bundleId));

            });
        });

        JSONArray netAttributes = json.get("attributes").getValue();
        JSONArray netTags = json.get("tags").getValue();
        netTags.stream().forEach(tag -> netPlan.addTag(tag.getValue()));
        netAttributes.stream().forEach(att ->
        {
            JSONObject attribute = att.getValue();
            netPlan.setAttribute(attribute.get("key").getValue(), (String)attribute.get("value").getValue());
        });

    }

    @Override
    public void create(NetPlan netPlan, XMLStreamReader2 xmlStreamReader) throws XMLStreamException
    {
        ProfileUtils.printTime("" , -1);
        this.hasAlreadyReadOneLayer = false;
        this.xmlStreamReader = xmlStreamReader;
        this.backupRouteIdsMap = new TreeMap<Route,List<Long>>();
        this.nodeAndLayerToIconURLMap = new TreeMap<> ();

        parseNetwork(netPlan);

        if (ErrorHandling.isDebugEnabled()) netPlan.checkCachesConsistency();
        ProfileUtils.printTime("Reading n2p file");
    }

    protected void parseNetwork(NetPlan netPlan) throws XMLStreamException
    {
        final Long nexElementId_thisNetPlan = getLong ("nextElementId");
        netPlan.currentPlotNodeLayout = NetPlan.PLOTLAYTOUT_DEFAULTNODELAYOUTNAME;
        netPlan.cache_definedPlotNodeLayouts = new TreeSet<> (); netPlan.cache_definedPlotNodeLayouts.add(NetPlan.PLOTLAYTOUT_DEFAULTNODELAYOUTNAME);
        try { netPlan.currentPlotNodeLayout = getString ("currentPlotNodeLayout"); } catch (Exception e) {}
        try
        {
            final String info = getString ("cache_definedPlotNodeLayouts");
            for (String s : StringUtils.readEscapedString_asStringList(info, Arrays.asList(NetPlan.PLOTLAYTOUT_DEFAULTNODELAYOUTNAME)))
                netPlan.cache_definedPlotNodeLayouts.add(s);
        } catch (Exception e) { e.printStackTrace(); }

        netPlan.setDescription(getStringOrDefault("description", ""));
        netPlan.setName(getStringOrDefault("name", ""));
        netPlan.nextElementId = new MutableLong(nexElementId_thisNetPlan);
        if (netPlan.nextElementId.toLong() <= 0) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        while (true) { try { netPlan.addGlobalPlanningDomain(getString ("planningDomain_" + (netPlan.getGlobalPlanningDomains().size())));  } catch(Exception e) { break; }   }

        while(xmlStreamReader.hasNext())
        {
            xmlStreamReader.next();
//			System.out.println(xmlStreamReader.getName().toString());
            switch(xmlStreamReader.getEventType())
            {
                case XMLEvent.START_ELEMENT:
                    String startElementName = xmlStreamReader.getName().toString();
                    switch(startElementName)
                    {
                        case "tag":
                            netPlan.addTag(xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value")));
                            break;
                        case "attribute":
                            String key = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "key"));
                            String name = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value"));
                            netPlan.setAttribute(key, name);
                            break;

                        case "layer":
                            parseLayer(netPlan);
                            break;

                        case "node":
                            parseNode(netPlan);
                            break;

                        case "resource":
                            parseResource(netPlan);
                            break;

                        case "srg":
                            parseSRG(netPlan);
                            break;

                        case "layerCouplingDemand":
                            final long upperLayerLinkId = getLong ("upperLayerLinkId");
                            final long lowerLayerDemandId = getLong ("lowerLayerDemandId");
                            netPlan.getDemandFromId(lowerLayerDemandId).coupleToUpperOrSameLayerLink(netPlan.getLinkFromId(upperLayerLinkId));
                            break;

                        case "layerCouplingMulticastDemand":
                            final long lowerLayerMulticastDemandId = getLong ("lowerLayerDemandId");
                            final SortedSet<Link> setLinksToCouple = getLinkSetFromIds(netPlan , getListLong("upperLayerLinkIds"));
                            netPlan.getMulticastDemandFromId(lowerLayerMulticastDemandId).couple(setLinksToCouple);
                            break;

                        case "sameLayerCouplingDemand":
                            final long coupledDemandId = getLong ("layerDemandId");
                            final long bundleId = getLong ("layerLinkId");
                            netPlan.getDemandFromId(coupledDemandId).coupleToUpperOrSameLayerLink(netPlan.getLinkFromId(bundleId));
                            break;

                        default:
                            throw new RuntimeException("Bad");
                    }

                    break;

                case XMLEvent.END_ELEMENT:
                    String endElementName = xmlStreamReader.getName().toString();
                    if (endElementName.equals("network"))
                    {
//						if (!netPlan.hasReadLayerZero && netPlan.layers.size() > 1) netPlan.removeNetworkLayer (netPlan.layers.get(0));
                        return;
                    }

                    break;
            }
        }

        throw new RuntimeException("'Network' element not parsed correctly (end tag not found)");
    }

    private void parseNode(NetPlan netPlan) throws XMLStreamException
    {
        final long nodeId = getLong ("id");
        if (nodeId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final double xCoord = getDouble ("xCoord");
        final double yCoord = getDouble ("yCoord");
        final String nodeName = getStringOrDefault ("name" , "");
        double population = 0; try { population = getDouble ("population"); } catch (Exception e) {}
        String siteName = null; try { siteName = getString ("siteName"); } catch (Exception e) {}
        boolean isUp = true; try { isUp = getBoolean ("isUp"); } catch (Exception e) {}
        final SortedSet<String> planningDomains = new TreeSet<> ();
        while (true) { try { planningDomains.add(getString ("planningDomain_" + (planningDomains.size())));  } catch(Exception e) { break; }   }

        Node newNode = netPlan.addNode(nodeId , xCoord, yCoord, nodeName, null);
        newNode.setDescription(getStringOrDefault ("description" , ""));

        try
        {
            final String mapLayoutString = getString ("mapLayout2NodeXYPositionMap");
            final SortedMap<String, Point2D> map = new TreeMap<> ();
            final List<String> info = StringUtils.readEscapedString_asStringList(mapLayoutString, Arrays.asList());
            if (info.size () % 3 != 0) throw new Exception ();
            for (int cont = 0; cont < info.size() / 3 ; cont ++)
            {
                final String layoutName = info.get(cont*3);
                final double x = Double.parseDouble(info.get(cont*3 + 1));
                final double y = Double.parseDouble(info.get(cont*3 + 2));
                map.put(layoutName, new UnmodifiablePoint2D(x, y));
            }
            map.entrySet().forEach(e->newNode.setXYPositionMap(e.getValue(), e.getKey()));
        } catch (Exception e) { e.printStackTrace();}

        for (String pd : planningDomains) newNode.addToPlanningDomain(pd);
        newNode.setFailureState(isUp);
        newNode.setPopulation(population);
        if (siteName != null) newNode.setSiteName(siteName);

        /* read the icons information and put it in a map for later (layers are not created yet!) */
        for (long layerId : getListLong("layersWithIconsDefined"))
        {
            List<Triple<Node,URL,Double>> iconsThisLayerSoFar = nodeAndLayerToIconURLMap.get (layerId);
            if (iconsThisLayerSoFar == null) { iconsThisLayerSoFar = new LinkedList<> (); nodeAndLayerToIconURLMap.put(layerId , iconsThisLayerSoFar); }
            URL url = null;
            Double relativeSize = null;
            try
            {
                url = new URL (getString ("nodeIconURLLayer_" + layerId));
                relativeSize = getDouble("nodeIconRelativeSizeLayer_" + layerId);

            } catch (Exception e) {}
            iconsThisLayerSoFar.add(Triple.of(newNode , url , relativeSize));
        }

        readAndAddAttributesToEndAndPdForNodes(newNode, "node");
    }

    private void parseDemand(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        final long demandId = getLong ("id");
        if (demandId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final long ingressNodeId = getLong ("ingressNodeId");
        final long egressNodeId = getLong ("egressNodeId");
        final double offeredTraffic = getDouble ("offeredTraffic");
        double maximumAcceptableE2EWorstCaseLatencyInMs = -1; try { maximumAcceptableE2EWorstCaseLatencyInMs = getDouble ("maximumAcceptableE2EWorstCaseLatencyInMs"); } catch (Throwable e) {}
        double offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth = 0; try { offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth = getDouble ("offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth"); } catch (Throwable e) {}
        String qosType = ""; try { qosType = getString("qosType"); } catch (Throwable e) {}

        Demand.IntendedRecoveryType recoveryType;
        try { recoveryType = Demand.IntendedRecoveryType.valueOf(getString("intendedRecoveryType")); }
        catch (XMLStreamException e) { recoveryType = Demand.IntendedRecoveryType.NOTSPECIFIED; }
        catch (Exception e) { recoveryType = Demand.IntendedRecoveryType.UNKNOWNTYPE; }
        Constants.RoutingType routingType = null;
        try { routingType = Constants.RoutingType.valueOf(getString("routingType")); } catch (Exception e) {  }
        long bidirectionalPairId = -1; try { bidirectionalPairId = getLong ("bidirectionalPairId"); } catch (Exception e) {}

        Demand newDemand = netPlan.addDemand(demandId , netPlan.getNodeFromId(ingressNodeId), netPlan.getNodeFromId(egressNodeId), offeredTraffic, routingType != null? routingType : Constants.RoutingType.SOURCE_ROUTING,null , netPlan.getNetworkLayerFromId(layerId));
        newDemand.setIntendedRecoveryType(recoveryType);
        newDemand.setOfferedTrafficPerPeriodGrowthFactor(offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth);
        newDemand.setMaximumAcceptableE2EWorstCaseLatencyInMs(maximumAcceptableE2EWorstCaseLatencyInMs);
        newDemand.setQoSType(qosType);
        newDemand.setName(getStringOrDefault("name", ""));
        newDemand.setDescription(getStringOrDefault("description", ""));
        try
        {
            final List<String> rows = StringUtils.readEscapedString_asStringList (getString("monitoredOrForecastedTraffics") , new ArrayList<> ());
            final TrafficSeries readTimeSerie = TrafficSeries.createFromStringList(rows);
            newDemand.setMonitoredOrForecastedOfferedTraffic(readTimeSerie);
        } catch (Exception e) {}

        if (routingType == null) newNpDemandsWithRoutingTypeNotDefined.add(newDemand);
        final Demand bidirPairDemand = bidirectionalPairId == -1? null : netPlan.getDemandFromId(bidirectionalPairId);
        if (bidirPairDemand != null)
        {
            if (bidirPairDemand.isBidirectional()) throw new RuntimeException ();
            bidirPairDemand.setBidirectionalPair(newDemand);
        }

        List<String> mandatorySequenceOfTraversedResourceTypes = new LinkedList<String> ();
        boolean finalElementRead = false;
        while(xmlStreamReader.hasNext() && !finalElementRead)
        {
            xmlStreamReader.next();

            switch(xmlStreamReader.getEventType())
            {
                case XMLEvent.START_ELEMENT:
                    String startElementName = xmlStreamReader.getName().toString();
                    switch(startElementName)
                    {
                        case "tag":
                            newDemand.addTag(xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value")));
                            break;
                        case "attribute":
                            String key = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "key"));
                            String name = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value"));
                            newDemand.setAttribute(key, name);
                            break;

                        case "serviceChainResourceTypeOfSequence":
                            String type = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "type"));
                            mandatorySequenceOfTraversedResourceTypes.add(type);
                            break;

                        default:
                            throw new RuntimeException("Bad");
                    }

                    break;

                case XMLEvent.END_ELEMENT:
                    String endElementName = xmlStreamReader.getName().toString();
                    if (endElementName.equals("demand")) { finalElementRead = true; break; }
            }
        }

        if (!finalElementRead) throw new RuntimeException("'Demand' element not parsed correctly (end tag not found)");
        if (!mandatorySequenceOfTraversedResourceTypes.isEmpty() && routingType == null)
        {
            newDemand.setRoutingType(Constants.RoutingType.SOURCE_ROUTING);
            newNpDemandsWithRoutingTypeNotDefined.remove(newDemand);
        }
        if (!mandatorySequenceOfTraversedResourceTypes.isEmpty())
            newDemand.setServiceChainSequenceOfTraversedResourceTypes(mandatorySequenceOfTraversedResourceTypes);
    }

    private void parseRoute(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        final long routeId = getLong ("id");
        if (routeId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final long demandId = getLong ("demandId");

        final double currentCarriedTrafficIfNotFailing = getDouble ("currentCarriedTrafficIfNotFailing");
        final List<Double> currentLinksAndResourcesOccupationIfNotFailing = getListDouble("currentLinksAndResourcesOccupationIfNotFailing");
        final List<NetworkElement> currentPath = getLinkAndResourceListFromIds(netPlan, getListLong("currentPath"));

        /* Initial route may not exist, if so current equals the initial */
        List<NetworkElement> initialStatePath = new ArrayList<NetworkElement> (currentPath);
        boolean initialPathExists = true;
        try { initialStatePath = getLinkAndResourceListFromIds(netPlan, getListLong("initialStatePath")); } catch (Exception e) { initialPathExists = false; }
        final double initialStateCarriedTrafficIfNotFailing = initialPathExists? getDouble ("initialStateCarriedTrafficIfNotFailing") : currentCarriedTrafficIfNotFailing;
        final List<Double> initialStateOccupationIfNotFailing = initialPathExists? getListDouble("initialStateOccupationIfNotFailing") : new ArrayList<Double> (currentLinksAndResourcesOccupationIfNotFailing);
        long bidirectionalPairId = -1; try { bidirectionalPairId = getLong ("bidirectionalPairId"); } catch (Exception e) {}
        final Demand newNetPlanDemand = netPlan.getDemandFromId(demandId);
        if (newNpDemandsWithRoutingTypeNotDefined.contains(newNetPlanDemand))
        {
            newNetPlanDemand.setRoutingType(Constants.RoutingType.SOURCE_ROUTING);
            newNpDemandsWithRoutingTypeNotDefined.remove(newNetPlanDemand);
        }
        final Route newRoute = netPlan.addServiceChain(routeId , newNetPlanDemand, initialStateCarriedTrafficIfNotFailing,
                initialStateOccupationIfNotFailing, initialStatePath, null);
        newRoute.setPath(currentCarriedTrafficIfNotFailing, currentPath, currentLinksAndResourcesOccupationIfNotFailing);
        newRoute.setName(getStringOrDefault("name", ""));
        newRoute.setDescription(getStringOrDefault("description", ""));

        final Route bidirPairRoute = bidirectionalPairId == -1? null : netPlan.getRouteFromId(bidirectionalPairId);
        if (bidirPairRoute != null)
        {
            if (bidirPairRoute.isBidirectional()) throw new RuntimeException ();
            bidirPairRoute.setBidirectionalPair(newRoute);
        }

        /* To be added at the end: backup routes may not exist yet */
        this.backupRouteIdsMap.put(newRoute ,  getListLong ("backupRoutes"));

        readAndAddAttributesToEndAndPdForNodes(newRoute, "route");
    }


    private void parseForwardingRule(NetPlan netPlan, long layerId , DoubleMatrix2D f_de) throws XMLStreamException
    {
        final long linkId = getLong ("linkId");
        final long demandId = getLong ("demandId");
        final double splittingRatio = getDouble ("splittingRatio");
        final Demand newNetPlanDemand = netPlan.getDemandFromId(demandId);
        if (newNpDemandsWithRoutingTypeNotDefined.contains(newNetPlanDemand))
        {
            newNetPlanDemand.setRoutingType(Constants.RoutingType.HOP_BY_HOP_ROUTING);
            newNpDemandsWithRoutingTypeNotDefined.remove(newNetPlanDemand);
        }
        f_de.set (netPlan.getDemandFromId(demandId).index , netPlan.getLinkFromId(linkId).index  , splittingRatio);
        readAndAddAttributesToEndAndPdForNodes(null, "forwardingRule");
    }

    private void parseHopByHopRouting(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        final NetworkLayer layer = netPlan.getNetworkLayerFromId(layerId);
        final int D = netPlan.getNumberOfDemands(layer);
        final int E = netPlan.getNumberOfLinks(layer);
        DoubleMatrix2D f_de = DoubleFactory2D.sparse.make (D,E);

        while(xmlStreamReader.hasNext())
        {
            xmlStreamReader.next();

            switch(xmlStreamReader.getEventType())
            {
                case XMLEvent.START_ELEMENT:
                    String startElementName = xmlStreamReader.getName().toString();
                    switch(startElementName)
                    {
                        case "forwardingRule":
                            parseForwardingRule(netPlan, layerId,f_de);
                            break;

                        default:
                            throw new RuntimeException("Bad");
                    }
                    break;

                case XMLEvent.END_ELEMENT:
                    String endElementName = xmlStreamReader.getName().toString();
                    if (endElementName.equals("hopByHopRouting"))
                    {
                        NetworkLayer thisLayer = netPlan.getNetworkLayerFromId(layerId);
                        netPlan.setForwardingRules(f_de , new TreeSet<> (netPlan.getDemandsHopByHopRouted(thisLayer)) , thisLayer);
                        return;
                    }
                    break;
            }
        }

        throw new RuntimeException("'Hop-by-hop routing' element not parsed correctly (end tag not found)");
    }

    private void parseLink(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        final long linkId = getLong ("id");
        if (linkId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final long originNodeId = getLong ("originNodeId");
        final long destinationNodeId = getLong ("destinationNodeId");
        final double capacity = getDouble ("capacity");
        final double lengthInKm = getDouble ("lengthInKm");
        final double propagationSpeedInKmPerSecond = getDouble ("propagationSpeedInKmPerSecond");
        long bidirectionalPairId = -1; try { bidirectionalPairId = getLong ("bidirectionalPairId"); } catch (Exception e) {}
        boolean isUp = true; try { isUp = getBoolean ("isUp"); } catch (Exception e) {}

        Link newLink = netPlan.addLink(linkId , netPlan.getNodeFromId(originNodeId), netPlan.getNodeFromId(destinationNodeId), capacity, lengthInKm, propagationSpeedInKmPerSecond, null , netPlan.getNetworkLayerFromId(layerId));
        newLink.setFailureState(isUp);
        newLink.setName(getStringOrDefault("name", ""));
        newLink.setDescription(getStringOrDefault("description", ""));
        try
        {
            final List<String> rows = StringUtils.readEscapedString_asStringList (getString("monitoredOrForecastedTraffics") , new ArrayList<> ());
            final TrafficSeries readTimeSerie = TrafficSeries.createFromStringList(rows);
            newLink.setMonitoredOrForecastedCarriedTraffic(readTimeSerie);
        } catch (Exception e) {}
        final Link bidirPairLink = bidirectionalPairId == -1? null : netPlan.getLinkFromId(bidirectionalPairId);
        if (bidirPairLink != null)
        {
            if (bidirPairLink.isBidirectional()) throw new RuntimeException ();
            bidirPairLink.setBidirectionalPair(newLink);
        }
        readAndAddAttributesToEndAndPdForNodes(newLink, "link");
    }


    private void parseSRG(NetPlan netPlan) throws XMLStreamException
    {
        final long srgId = getLong ("id");
        if (srgId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final double meanTimeToFailInHours = getDouble ("meanTimeToFailInHours");
        final double meanTimeToRepairInHours = getDouble ("meanTimeToRepairInHours");
        boolean isDynamic = false; try { isDynamic = getBoolean("isDynamic"); } catch (Exception e) {}
        SharedRiskGroup newSRG = null;
        if (isDynamic)
        {
            final String className = getString("dynamicSrgClassName");
            final String configString = getString("dynamicSrgConfigString");
            newSRG = netPlan.addSRGDynamic(srgId , meanTimeToFailInHours, meanTimeToRepairInHours, className , configString , null);
        }
        else
        {
            newSRG = netPlan.addSRG(srgId , meanTimeToFailInHours, meanTimeToRepairInHours, null);
            SortedSet<Node> srgNodes = getNodeSetFromIds(netPlan, getListLong("nodes"));
            SortedSet<Link> srgLinks = getLinkSetFromIds(netPlan, getListLong("links"));
            for (Node n : srgNodes) newSRG.addNode(n);
            for (Link e : srgLinks) newSRG.addLink(e);
        }
        newSRG.setName(getStringOrDefault("name", ""));
        newSRG.setDescription(getStringOrDefault("description", ""));

        readAndAddAttributesToEndAndPdForNodes(newSRG, "srg");
    }

    private void parseResource(NetPlan netPlan) throws XMLStreamException
    {
        final long resId = getLong ("id");
        if (resId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final long hostNodeId = getLong ("hostNodeId");
        final boolean isAttachedToANode = hostNodeId != -1;
        if (isAttachedToANode && netPlan.getNodeFromId(hostNodeId) == null) throw new Net2PlanException ("Could not find the hot node of a resource when reading");
        final String type = getString ("type");
        final String name = getString ("name");
        final String capacityMeasurementUnits = getString ("capacityMeasurementUnits");
        final double processingTimeToTraversingTrafficInMs = getDouble ("processingTimeToTraversingTrafficInMs");
        final double capacity = getDouble ("capacity");

        URL urlIcon = null; try { urlIcon = new URL (getString ("urlIcon")); } catch (Exception e) {}
        final List<Double> baseResourceAndOccupiedCapacitiesMap = getListDouble("baseResourceAndOccupiedCapacitiesMap");
        SortedMap<Resource,Double> occupiedCapacitiesInBaseResources = getResourceOccupationMap(netPlan, baseResourceAndOccupiedCapacitiesMap);
        final Optional<Node> hostNode = isAttachedToANode? Optional.of(netPlan.getNodeFromId(hostNodeId)): Optional.empty();
        Resource newResource = netPlan.addResource(resId , type , name , hostNode , capacity , capacityMeasurementUnits ,
                occupiedCapacitiesInBaseResources , processingTimeToTraversingTrafficInMs , null);
        newResource.setUrlIcon(urlIcon);
        newResource.setName(getStringOrDefault("name", ""));
        newResource.setDescription(getStringOrDefault("description", ""));
        readAndAddAttributesToEndAndPdForNodes(newResource, "resource");
    }

    private void parseLayer(NetPlan netPlan) throws XMLStreamException
    {
        final long layerId = getLong ("id");
        if (layerId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final String demandTrafficUnitsName = getString ("demandTrafficUnitsName");
        final String layerDescription = getStringOrDefault ("description" , "");
        final String layerName = getStringOrDefault ("name" , "");
        final String linkCapacityUnitsName = getString ("linkCapacityUnitsName");
        URL defaultNodeIconURL = null;
        try { defaultNodeIconURL = new URL (getString ("defaultNodeIconURL")); } catch (Exception e) {}
        final boolean isDefaultLayer = getBoolean ("isDefaultLayer");

        NetworkLayer newLayer;
        if (!hasAlreadyReadOneLayer)
        {
            if (netPlan.layers.size() != 1) throw new RuntimeException ("Bad");
            if (netPlan.layers.get (0).id != layerId)
            {
                // the Id of first layer is different => create a new one and remove the existing
                newLayer = netPlan.addLayer(layerId , layerName, layerDescription, linkCapacityUnitsName, demandTrafficUnitsName, defaultNodeIconURL , null);
                netPlan.removeNetworkLayer(netPlan.layers.get (0));
            }
            else
            {
                newLayer = netPlan.layers.get (0); // it already has the right Id
                newLayer.demandTrafficUnitsName = demandTrafficUnitsName;
                newLayer.description = layerDescription;
                newLayer.name = layerName;
                newLayer.linkCapacityUnitsName= linkCapacityUnitsName;
            }
            hasAlreadyReadOneLayer = true;
        }
        else
        {
            newLayer = netPlan.addLayer(layerId , layerName, layerDescription, linkCapacityUnitsName, demandTrafficUnitsName, defaultNodeIconURL , null);
        }

        /* write the node icons information, that is already there */
        if (nodeAndLayerToIconURLMap.containsKey(newLayer.getId()))
            for (Triple<Node,URL,Double> iconInfo : nodeAndLayerToIconURLMap.get(newLayer.getId()))
                iconInfo.getFirst().setUrlNodeIcon(newLayer , iconInfo.getSecond(),iconInfo.getThird() == null? 1.0 : iconInfo.getThird());

        if (isDefaultLayer) netPlan.setNetworkLayerDefault(newLayer);

        while(xmlStreamReader.hasNext())
        {
            xmlStreamReader.next();

            switch(xmlStreamReader.getEventType())
            {
                case XMLEvent.START_ELEMENT:
                    String startElementName = xmlStreamReader.getName().toString();
                    switch(startElementName)
                    {
                        case "tag":
                            newLayer.addTag(xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value")));
                            break;

                        case "attribute":
                            newLayer.setAttribute(getString ("key"), getString ("value"));
                            break;

                        case "demand":
                            parseDemand(netPlan, layerId);
                            break;

                        case "multicastDemand":
                            parseMulticastDemand(netPlan, layerId);
                            break;

                        case "multicastTree":
                            parseMulticastTree(netPlan, layerId);
                            break;

                        case "link":
                            parseLink(netPlan, layerId);
                            break;

                        case "hopByHopRouting":
                            parseHopByHopRouting(netPlan, layerId);
                            break;

                        case "sourceRouting":
                            parseSourceRouting(netPlan, layerId);
                            break;

                        default:
                            throw new RuntimeException("Bad child (" + startElementName + ") for layer element");
                    }
                    break;

                case XMLEvent.END_ELEMENT:
                    String endElementName = xmlStreamReader.getName().toString();
                    if (endElementName.equals("layer")) return;
                    break;
            }
        }

        throw new RuntimeException("'Layer' element not parsed correctly (end tag not found)");
    }

    private void parseMulticastDemand(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        final long demandId = getLong ("id");
        if (demandId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final long ingressNodeId = getLong ("ingressNodeId");
        SortedSet<Node> newEgressNodes = getNodeSetFromIds(netPlan ,  getListLong("egressNodeIds"));
        final double offeredTraffic = getDouble ("offeredTraffic");
        double maximumAcceptableE2EWorstCaseLatencyInMs = -1; try { maximumAcceptableE2EWorstCaseLatencyInMs = getDouble ("maximumAcceptableE2EWorstCaseLatencyInMs"); } catch (Throwable e) {}
        double offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth = 0; try { offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth = getDouble ("offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth"); } catch (Throwable e) {}
        String qosType = ""; try { qosType = getString("qosType"); } catch (Throwable e) {}

        final MulticastDemand newDemand = netPlan.addMulticastDemand(demandId , netPlan.getNodeFromId(ingressNodeId), newEgressNodes , offeredTraffic, null , netPlan.getNetworkLayerFromId(layerId));
        newDemand.setMaximumAcceptableE2EWorstCaseLatencyInMs(maximumAcceptableE2EWorstCaseLatencyInMs);
        newDemand.setOfferedTrafficPerPeriodGrowthFactor(offeredTrafficGrowthFactorPerPeriodZeroIsNoGrowth);
        newDemand.setQoSType(qosType);
        newDemand.setName(getStringOrDefault("name", ""));
        newDemand.setDescription(getStringOrDefault("description", ""));
        try
        {
            final List<String> rows = StringUtils.readEscapedString_asStringList (getString("monitoredOrForecastedTraffics") , new ArrayList<> ());
            final TrafficSeries readTimeSerie = TrafficSeries.createFromStringList(rows);
            newDemand.setMonitoredOrForecastedOfferedTraffic(readTimeSerie);
        } catch (Exception e) {}
        readAndAddAttributesToEndAndPdForNodes(newDemand, "multicastDemand");
    }

    private void parseSourceRouting(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        this.backupRouteIdsMap.clear(); // in multiple layers, we have to refresh this

        while(xmlStreamReader.hasNext())
        {
            xmlStreamReader.next();

            switch(xmlStreamReader.getEventType())
            {
                case XMLEvent.START_ELEMENT:
                    String startElementName = xmlStreamReader.getName().toString();
                    switch(startElementName)
                    {
                        case "route":
                            parseRoute(netPlan, layerId);
                            break;

                        default:
                            throw new RuntimeException("Bad: " + startElementName);
                    }
                    break;

                case XMLEvent.END_ELEMENT:
                    String endElementName = xmlStreamReader.getName().toString();
                    if (endElementName.equals("sourceRouting"))
                    {
                        /* Before returning, we add the backup routes */
                        for (Map.Entry<Route,List<Long>> entry : this.backupRouteIdsMap.entrySet())
                        {
                            final Route primary = entry.getKey();
                            for (long backupId : entry.getValue()) primary.addBackupRoute(netPlan.getRouteFromId(backupId));
                        }
                        return;
                    }
                    break;
            }
        }

        throw new RuntimeException("'Source routing' element not parsed correctly (end tag not found)");
    }

    private void parseMulticastTree(NetPlan netPlan, long layerId) throws XMLStreamException
    {
        final long treeId = getLong ("id");
        if (treeId >= netPlan.nextElementId.toLong()) throw new Net2PlanException ("A network element has an id higher than the nextElementId");
        final long demandId = getLong ("demandId");
        final double carriedTraffic = getDouble ("carriedTrafficIfNotFailing");
        double occupiedCapacity = getDouble ("occupiedLinkCapacityIfNotFailing");
        double carriedTrafficIfNotFailing = carriedTraffic; try { carriedTrafficIfNotFailing = getDouble ("carriedTrafficIfNotFailing"); } catch (Exception e) {}
        double occupiedLinkCapacityIfNotFailing = occupiedCapacity; try { occupiedLinkCapacityIfNotFailing = getDouble ("occupiedLinkCapacityIfNotFailing"); } catch (Exception e) {}
        if (occupiedCapacity < 0) occupiedCapacity = carriedTraffic;

        final MulticastDemand demand = netPlan.getMulticastDemandFromId(demandId);
        final SortedSet<Link> initialSetLinks_link = getLinkSetFromIds(netPlan, getListLong ("initialSetLinks"));
        final SortedSet<Link> currentSetLinks_link = getLinkSetFromIds(netPlan, getListLong ("currentSetLinks"));
        final MulticastTree newTree = netPlan.addMulticastTree(treeId , demand , carriedTrafficIfNotFailing , occupiedLinkCapacityIfNotFailing , initialSetLinks_link , null);
        newTree.setLinks(currentSetLinks_link);
        newTree.setName(getStringOrDefault("name", ""));
        newTree.setDescription(getStringOrDefault("description", ""));
        readAndAddAttributesToEndAndPdForNodes (newTree , "multicastTree");
    }

    private String getStringOrDefault (String name , String defaultVal)
    {
        try
        {
            return xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, name));
        } catch (Exception e) { /*e.printStackTrace();*/ return defaultVal; }
    }
    private boolean getBoolean (String name) { return Boolean.parseBoolean(xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, name))); }
    private String getString (String name) throws XMLStreamException
    {
        return xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, name));
    }
    private long getLong (String name) throws XMLStreamException 	{ return xmlStreamReader.getAttributeAsLong(xmlStreamReader.getAttributeIndex(null, name)); }
    private List<Long> getListLong (String name) throws XMLStreamException 	{ return LongUtils.toList(xmlStreamReader.getAttributeAsLongArray(xmlStreamReader.getAttributeIndex(null, name))); }
    private List<Double> getListDouble (String name) throws XMLStreamException 	{ return DoubleUtils.toList(xmlStreamReader.getAttributeAsDoubleArray(xmlStreamReader.getAttributeIndex(null, name))); }
    private double getDouble (String name) throws XMLStreamException 	{ return xmlStreamReader.getAttributeAsDouble(xmlStreamReader.getAttributeIndex(null, name)); }
    private static SortedSet<Link> getLinkSetFromIds (NetPlan np , Collection<Long> ids)
    {
        SortedSet<Link> res = new TreeSet<Link> (); for (long id : ids) res.add(np.getLinkFromId(id)); return res;
    }
    private static SortedSet<Node> getNodeSetFromIds (NetPlan np , Collection<Long> ids)
    {
        SortedSet<Node> res = new TreeSet<Node> (); for (long id : ids) res.add(np.getNodeFromId(id)); return res;
    }
    private static List<Link> getLinkListFromIds (NetPlan np , Collection<Long> ids)
    {
        List<Link> res = new LinkedList<Link> (); for (long id : ids) res.add(np.getLinkFromId(id)); return res;
    }
    private static List<NetworkElement> getLinkAndResourceListFromIds(NetPlan np , Collection<Long> ids)
    {
        List<NetworkElement> res = new LinkedList<NetworkElement> ();
        for (long id : ids)
        {
            NetworkElement e = np.getLinkFromId(id);
            if (e == null) e = np.getResourceFromId(id);
            if (e == null) throw new Net2PlanException ("Unknown id in the list");
            res.add(e);
        }
        return res;
    }
    private static SortedMap<Resource,Double> getResourceOccupationMap (NetPlan np , List<Double> resAndOccList)
    {
        SortedMap<Resource,Double> res = new TreeMap<Resource,Double> ();
        Iterator<Double> it = resAndOccList.iterator();
        while (it.hasNext())
        {
            final long id = (long) (double) it.next();
            if (!it.hasNext()) throw new Net2PlanException ("Wrong array size");
            final double val = it.next();
            final Resource r = np.getResourceFromId(id); if (r == null) throw new Net2PlanException ("Unknown resource id");
            res.put(r, val);
        }
        return res;
    }

    private void readAndAddAttributesToEndAndPdForNodes (NetworkElement updateElement , String endingTag) throws XMLStreamException
    {
        while(xmlStreamReader.hasNext())
        {
            xmlStreamReader.next();

            switch(xmlStreamReader.getEventType())
            {
                case XMLEvent.START_ELEMENT:
                    String startElementName = xmlStreamReader.getName().toString();
                    switch(startElementName)
                    {
                        case "tag":
                            updateElement.addTag(xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value")));
                            break;

                        case "attribute":
                            String key = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "key"));
                            String name = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "value"));
                            if (updateElement != null) updateElement.setAttribute(key, name);
                            break;

                        default:
                            throw new RuntimeException("Bad");
                    }

                    break;

                case XMLEvent.END_ELEMENT:
                    String endElementName = xmlStreamReader.getName().toString();
                    if (endElementName.equals(endingTag)) return;
                    break;
            }
        }

        throw new RuntimeException("'" + endingTag +"' tag not parsed correctly (end tag not found)");

    }
}
