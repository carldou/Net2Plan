package com.net2plan.examples.niw.algorithms;

import com.google.common.collect.Lists;
import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Resource;
import com.net2plan.libraries.GraphUtils;
import com.net2plan.niw.*;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Pair;
import com.net2plan.utils.Triple;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VnfAlphaPlacement implements IAlgorithm {
	
	
	List<Double> latencies =  new ArrayList <Double>();

	@Override
	public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters,Map<String, String> net2planParameters) {
		
		final Double alpha = Double.parseDouble(algorithmParameters.get("alpha"));
		final boolean concentration = Boolean.parseBoolean(algorithmParameters.get("concentration"));
		final File folder = new File ("Results");
		if (folder.exists() && folder.isFile()) throw new Net2PlanException ("The folder is a file");
		if (!folder.exists()) folder.mkdirs();

			
			WNet wNet = runAlgorithm(netPlan, algorithmParameters);

			/* Dimension the VNF instances, consuming the resources CPU, HD, RAM */
			/*
			 * for(WVnfInstance vnf : wNet.getVnfInstances())
			 * vnf.scaleVnfCapacityAndConsumptionToBaseInstanceMultiple();
			 */

			// HDD 	0.0425€/GB	(85€ 	-> 		2000GB)
			// CPU	35€/Core	(70€	->		2 Cores)
			// RAM 	12.5€/GB	(50€	->		4GB)

			
			//SORTED BY NAME
			double nodeCost = 0;
			double finalCost = 0;
			List<WLightpath> lpList = wNet.getLightpaths();
			
			final List<String> namesString =  new ArrayList<>();
			final List<String> costString = new ArrayList<> ();
			final List<String> summaryString = new ArrayList<> ();
			
			for(WNode node : wNet.getNodes()) {
				namesString.add("'"+node.getName()+"' ");
				nodeCost = Math.sqrt((85/2000)*node.getOccupiedHdGB()+(70/2)*node.getOccupiedCpus()+(50/4)*node.getOccupiedRamGB());
				finalCost += nodeCost;
				costString.add(Double.toString(nodeCost));
			}
			
			summaryString.add(Double.toString(finalCost));
			summaryString.add(Double.toString(lpList.size()));
			summaryString.add(Double.toString(Collections.max(latencies)));
			summaryString.add(Double.toString(Collections.min(latencies)));
			summaryString.add(Double.toString(latencies.stream().mapToDouble(val -> val).average().orElse(0.0)));
			for(String st : costString) summaryString.add(st);
			
			
			writeFileInOneLine (new File (folder , "names_sortedByName"+ ".txt") , namesString);
			writeFile (new File (folder , alpha+"_"+concentration + ".txt") , summaryString);

			
			//SORTED BY NUMBER OF VNFS INSTANTIATED
			/*namesString.clear();
			costString.clear();
			summaryString.clear();
			finalCost = 0;

			Comparator<WNode> comparator = new Comparator<WNode>() {
			    @Override
			    public int compare(WNode A, WNode B) {
			        return (int) (B.getAllVnfInstances().size() - A.getAllVnfInstances().size());
			    }
			};
			
			List<WNode> nodesSortedByPopulation = wNet.getNodes();
			Collections.sort(nodesSortedByPopulation, comparator); 
			
			for(WNode node : nodesSortedByPopulation) {
				namesString.add("'"+node.getName()+"' ");
				nodeCost = Math.sqrt((85/2000)*node.getOccupiedHdGB()+(70/2)*node.getOccupiedCpus()+(50/4)*node.getOccupiedRamGB());
				finalCost += nodeCost;
				costString.add(Double.toString(finalCost));
			}
			
			summaryString.add(Double.toString(finalCost));
			summaryString.add(Double.toString(lpList.size()));
			for(String st : costString) summaryString.add(st);
			
			writeFileInOneLine (new File (folder , "names_sortedByNumberOfVNFs"+ ".txt") , namesString);
			writeFile (new File (folder , "sortedByNumberOfVNFs" + Lmax  + ".txt") , summaryString);*/

		return "Ok";
	}
	
	public WNet runAlgorithm (NetPlan netPlan, Map<String, String> algorithmParameters){
		
		final double alpha = Double.parseDouble(algorithmParameters.get("alpha"));
		final boolean VNFsInMCENb = Boolean.parseBoolean(algorithmParameters.get("VNFsInMCENb"));
		final boolean concentration = Boolean.parseBoolean(algorithmParameters.get("concentration"));
		
		final long seed = Long.parseLong(algorithmParameters.get("randomSeed"));
		final Random rng = new Random (seed);
		final Double Lmax = Double.parseDouble(algorithmParameters.get("Lmax"));
		final int K = Integer.parseInt(algorithmParameters.get("K"));
		final int numServices = Integer.parseInt(algorithmParameters.get("numServices"));
		final String excelFile = algorithmParameters.get("excelFile");
		File excelPath = new File(excelFile);
		WNet wNet = ImportMetroNetwork.importFromExcelFile(excelPath);
		netPlan.copyFrom(wNet.getNetPlan());
		wNet = new WNet(netPlan);
		final OpticalSpectrumManager osm = OpticalSpectrumManager.createFromRegularLps(wNet);
		final double linerate_Gbps = 10;
		final double Tmax_Gbps = 1.0;
		final int slotsPerLightpath = 4;

		// Perform here initial checks
		/*
		 * Remove any existing lightpath, VNF instances and any existing service chain
		 */
		for (WLightpathRequest lpr : wNet.getLightpathRequests())
			lpr.remove(); // this removes also the lps

		for (WVnfInstance vnf : wNet.getVnfInstances())
			vnf.remove();

		for (WServiceChain sc : wNet.getServiceChains())
			sc.remove();

		/* Create Services and VNFs */
		int totalNumberOfVNFS = 0;
		for (int i = 0; i < numServices; i++) {
			int numberOfVNFsInThisService = (int) randomWithRange(rng , 1, 5);
			List<String> vnfsUpstream = new ArrayList<String>();
			for (int j = 0; j < numberOfVNFsInThisService; j++) {
				WVnfType vnfType = new WVnfType("VNF"+j+"_Service"+i, 	//Name
						100000000, 				//Capacity_Gbps
						2, 						//CPU
						4, 						//RAM
						20, 					//HD
						1.0 , 					//Processing Time
						Optional.empty(), 		//Instantiation nodes
						"");					//Arbitrary Params
				wNet.addOrUpdateVnfType(vnfType);

				vnfsUpstream.add("VNF"+j+"_Service"+i);
			}
			List<String> vnfsDownstream = Lists.reverse(vnfsUpstream);
			final double trafficPerUserInGbps = randomWithRange(rng, 0.1, 1.0);
			List<Double> trafficExpansion = Collections.nCopies(numberOfVNFsInThisService, 1.0);
			List<Double> latencyInMs = Collections.nCopies(numberOfVNFsInThisService + 1, randomWithRange(rng , 0.5, Lmax));
			System.out.println(
					"Service" + i + " latency is: " + latencyInMs.get(0) + " and it has " + vnfsUpstream.size() + " VNFs");

			WUserService userService = new WUserService("Service" + i, // Name
														vnfsUpstream, // VNF Upstream
														vnfsDownstream, // VNF Downstream
														trafficExpansion, // Traffic expansion Up
														trafficExpansion, // Traffic expansion Down
														latencyInMs, // Max Latency Up
														latencyInMs, // Max Latency Down
														randomWithRange(rng , 0.1, 1.0), // Injection expansion
														false, // Ending in core node?
														""); // Arbitrary params
			userService.setArbitraryParamString(trafficPerUserInGbps + "");
			wNet.addOrUpdateUserService(userService);
			
			totalNumberOfVNFS += numberOfVNFsInThisService;
		}
		
		System.out.println("######################################################################################");
		System.out.println("Total number of VNFs: "+totalNumberOfVNFS);
		System.out.println("Total number of CPU: "+ (totalNumberOfVNFS*2));
		System.out.println("Total number of RAM: "+ (totalNumberOfVNFS*4));
		System.out.println("Total number of HD: "+ (totalNumberOfVNFS*20));
		System.out.println("######################################################################################");
		
		
		/* Adding lightpaths */
		for (WNode origin : wNet.getNodes()) {
			for (WNode destination : wNet.getNodes()) {
				if (!origin.equals(destination)) {	
					
					List<List<WFiber>> cpl = wNet.getKShortestWdmPath(K, origin, destination,Optional.empty());
					if(cpl.get(0).size() == 1) { // adjacent nodes
						List<WFiber> uniqueFiber = cpl.get(0);
						//if (wNet.getLightpaths().stream().filter(l->l.getA() == destination && l.getB() == origin).count() > 0) continue;
						WLightpathRequest lpr = wNet.addLightpathRequest(origin, destination, linerate_Gbps,false);
						Optional<SortedSet<Integer>> wl = osm.spectrumAssignment_firstFit(uniqueFiber,slotsPerLightpath, Optional.empty());
						if (wl.isPresent()) {
							WLightpath lp = lpr.addLightpathUnregenerated(uniqueFiber, wl.get(), false);
							osm.allocateOccupation(lp, uniqueFiber, wl.get());
							Pair<WIpLink,WIpLink> ipLink = wNet.addIpLinkBidirectional(origin, destination, linerate_Gbps);
							lpr.coupleToIpLink(ipLink.getFirst());
						}
					}
				}
			}
		}
		

		/* ############################# ALGORITHM ############################# */
		System.out.println("####################### STARTING ALGORITHM #######################");
		WNode greatestNode = getGreatestNode(wNet); // Necessary in order to calculate the traffic per service
		System.out.println(
				"The greatest node is " + greatestNode.getName() + " with " + greatestNode.getPopulation() + " people");

		/* Normalize so the maximum traffic of any service in any node is TMax */
		Map<Pair<WNode , WUserService> , Double> trafficGeneratedPerNodeServicePairNormalized = new HashMap<> ();
		for (WNode node : wNet.getNodes()) 
			for (WUserService service : wNet.getUserServicesInfo().values())
				trafficGeneratedPerNodeServicePairNormalized.put(Pair.of(node, service), node.getPopulation() * Double.parseDouble(service.getArbitraryParamString()));
		final List<Pair<WNode , WUserService>> orderedRequestsHigherToLowerTraffic = trafficGeneratedPerNodeServicePairNormalized.keySet().stream().
				sorted((p1,p2)->Double.compare(trafficGeneratedPerNodeServicePairNormalized.get(p2), trafficGeneratedPerNodeServicePairNormalized.get(p1))).
				collect(Collectors.toList());
		final double largestTrafficGeneratedGbps =  trafficGeneratedPerNodeServicePairNormalized.get(orderedRequestsHigherToLowerTraffic.get(0));
		for (Pair<WNode , WUserService> requestInfo : orderedRequestsHigherToLowerTraffic)
			trafficGeneratedPerNodeServicePairNormalized.put(requestInfo, trafficGeneratedPerNodeServicePairNormalized.get(requestInfo) * Tmax_Gbps / largestTrafficGeneratedGbps);
		
		/* Generate service chain requests */
		for (Pair<WNode , WUserService> requestInfo : orderedRequestsHigherToLowerTraffic)
		{
			final WNode node = requestInfo.getFirst();
			final WUserService service = requestInfo.getSecond();
			final double totalTrafficInGbpsOfThisServiceInThisNodeGbps = trafficGeneratedPerNodeServicePairNormalized.get(requestInfo);
			//System.out.println(totalTrafficInGbpsOfThisServiceInThisNodeGbps);
			WServiceChainRequest scr = wNet.addServiceChainRequest(node, true, service);
			scr.setCurrentOfferedTrafficInGbps(totalTrafficInGbpsOfThisServiceInThisNodeGbps);
		}				

		/* Algorithm starts here serving service chain requests */
		final List<WServiceChainRequest> orderedServiceChainRequestsHigherToLowerTraffic = wNet.getServiceChainRequests().stream().
				sorted((s1,s2)->Double.compare(s2.getCurrentOfferedTrafficInGbps(), s1.getCurrentOfferedTrafficInGbps())  ).
				collect(Collectors.toList());
		
		/* Iterate each ServiceChainRequest */
		int it = 0;
		int totalNumberOfIterations = orderedServiceChainRequestsHigherToLowerTraffic.size();
		for (WServiceChainRequest scr : orderedServiceChainRequestsHigherToLowerTraffic)
		{
		
			final WNode node = scr.getPotentiallyValidOrigins().first();
			final double totalTrafficInGbpsOfThisServiceInThisNodeGbps = scr.getCurrentOfferedTrafficInGbps();
			//final WUserService service = wNet.getUserServicesInfo().get(scr.getli);
			//double serviceLatency_ms = service.getListMaxLatencyFromInitialToVnfStart_ms_upstream().get(0);
			double serviceLatency_ms = scr.getListMaxLatencyFromOriginToVnStartAndToEndNode_ms().get(0);
			final WNode coreNode = getNearestCoreNode(netPlan, wNet, node).getAsNode();
			
			/*######################################### Some prints ############################################*/
			System.out.println("---------------------------------------------------------------");
			System.out.println("Iteration number "+ ++it +" out of "+totalNumberOfIterations);
			System.out.println("totalTrafficInGbpsOfThisServiceInThisNodeGbps: "+totalTrafficInGbpsOfThisServiceInThisNodeGbps);
			//System.out.println("Iteration " + node.getName() + " & " + service.getUserServiceUniqueId());
			System.out.println("Iteration " + node.getName() + " & " + scr.getId());
			//System.out.println(service.getUserServiceUniqueId() + " latency: " + truncate(serviceLatency_ms, 2));
			System.out.println(scr.getId() + " latency: " + truncate(serviceLatency_ms, 2));
			System.out.println("Core Node selected: " + coreNode.getName());
			System.out.println(node.getName() + " -> " + coreNode.getName());
			System.out.println("### Number of VNFs for service "+scr.getId()+": "+scr.getNumberVnfsToTraverse());
			/*##################################################################################################*/

			
			final List<String> vnfsToTraverse = scr.getSequenceVnfTypes();
			final int numberOfVnfsToTraverse = vnfsToTraverse.size();
			
			
			// first, if the node is a CoreNode the VNFS must be instantiated on it. It's the best solution possible.
			// #### Case 1: The node is Core Node ####
			if (coreNode.equals(node)) {
				System.out.println("Case 1: The node is a Core Node!!");
				System.out.println("Result: " + scr.getId() + " resources allocated in "
						+ node.getName());
				
				setServiceVNFSinEndingNode(wNet, scr, node);

				final List<List<WAbstractNetworkElement>> paths = wNet.getKShortestServiceChainInIpLayer(
						K, node, node, vnfsToTraverse, Optional.empty(), Optional.empty());
				System.out.println("#1 Allocating resources in the own node");
				System.out.println(paths.get(0));
				scr.addServiceChain(paths.get(0),totalTrafficInGbpsOfThisServiceInThisNodeGbps);

				latencies.add(0.2); //minimum latency
				System.out.println("Latency in this iteration: "+0.2);
				
				// else, we need to discover the option that meets the requirements.
			}else 
			{
				final Map<WFiber , Double> latencyInMsAssumingOeoInAllHops = wNet.getFibers().stream().collect(Collectors.toMap(e->e, e->0.2 + e.getLengthInKm () / 200.0 ));
				final List<List<WFiber>> kFiberLists = wNet.getKShortestWdmPath(K, node, coreNode, Optional.of(latencyInMsAssumingOeoInAllHops));
				final List<WFiber> firstFiberLinks = kFiberLists.get(0);	
					
				 if(VNFsInMCENb) {
					 
							List<String> serviceVNFS = scr.getSequenceVnfTypes();
							for (String vnfName : serviceVNFS) {
								wNet.addVnfInstance(coreNode, vnfName, wNet.getVnfType(vnfName).get());
							}
							final List<List<WAbstractNetworkElement>> paths = wNet.getKShortestServiceChainInIpLayer(K, node, coreNode, scr.getSequenceVnfTypes(), Optional.empty(), Optional.empty());
							scr.addServiceChain(paths.get(0), totalTrafficInGbpsOfThisServiceInThisNodeGbps);	
			
							System.out.println("VNFs in MCENb parameter enabled, all VNFs of "+ scr.getId() +" will be instantiated in: "+ coreNode.getName());
							System.out.print(node.getName());
							for(WFiber fiber : firstFiberLinks) {
								System.out.print(" #" + truncate(fiber.getLengthInKm(), 2) + "km,("
										+ truncate(fiber.getNe().getPropagationDelayInMs(), 2) + "ms)# "
										+ fiber.getB().getName());
							}
			
							System.out.println("Number of links to traverse (hops): " + firstFiberLinks.size());
							double propagationDelay = wNet.getPropagationDelay(firstFiberLinks);
							double latency = propagationDelay + 2 * 0.1 * firstFiberLinks.size();
							latencies.add(latency);
							System.out.println("latency = propagationDelay + 2 * 0.1 * fibers.size()");
							System.out.println("latency = "+propagationDelay+" + 2 * 0.1 * "+firstFiberLinks.size());
							System.out.println("Latency in this iteration: "+latency);
							
							
				}else
					{
					
						List<WNode> randomNodeSequence = new ArrayList<WNode>();
						int nVNFsInShortestPath = (int) Math.floor(alpha * numberOfVnfsToTraverse); 
						int nVNFsRandom = numberOfVnfsToTraverse - nVNFsInShortestPath;
						System.out.println("### Number of VNFs to instantiate in the shortest path (alpha = "+alpha+"): "+nVNFsInShortestPath);
					
						int numberOfHops = firstFiberLinks.size();
						
						// know nodes in shortest path as List<WNode>
						List <WNode> nodesInShortestPath = getNodesInPath(firstFiberLinks);			
						
						// VNFs allocation in the destination node of the SP.
						for (int i=0; i<nVNFsInShortestPath; i++) {
							String vnfName = vnfsToTraverse.get(numberOfVnfsToTraverse-1 - i);
							wNet.addVnfInstance(coreNode, vnfName, wNet.getVnfType(vnfName).get());
							System.out.println(vnfName+" allocated in "+coreNode.getName());
						}
						
						// Know valid nodes to instantiate VNFs out of the shortest path List<WNode>
						if(nVNFsInShortestPath != numberOfVnfsToTraverse) 
						{	
							List<WNode> candidateNodesOutOfTheSP = getNodesInRangeOutOfTheShortestPath(wNet, node, numberOfHops, nodesInShortestPath);
							
							if(concentration)
							{
								System.out.println("Instantiating VNFs depending on the previous concentration..");
								List<Integer> VNFsInNodes = new ArrayList<Integer>();
								for (WNode candidateNode : candidateNodesOutOfTheSP) {
									System.out.println(candidateNode.getName()+" has "+candidateNode.getVnfInstances().size()+ "VNFs already instantiated.");
									VNFsInNodes.add(candidateNode.getVnfInstances().size());
								}
								WNode nodeToInstantiateVNF = candidateNodesOutOfTheSP.get(VNFsInNodes.indexOf(Collections.max(VNFsInNodes)));
								
								for(int i=0; i<nVNFsRandom; i++) {
									String vnfName = scr.getSequenceVnfTypes().get(i);
									wNet.addVnfInstance(nodeToInstantiateVNF, vnfName, wNet.getVnfType(vnfName).get());
									randomNodeSequence.add(nodeToInstantiateVNF);
									int hops = wNet.getKShortestWdmPath(1, node, nodeToInstantiateVNF, Optional.empty()).get(0).size();
									System.out.println(vnfName+" allocated in "+nodeToInstantiateVNF.getName()+" ("+hops+" hops)");
								}
								
							}else
							{
								System.out.println("Instantiating VNFs randomly..");
								// Set Service VNFs randomly in a candidate node.	
								for(int i=0; i<nVNFsRandom; i++) {
									String vnfName = scr.getSequenceVnfTypes().get(i);		
									WNode randomNode = candidateNodesOutOfTheSP.get((int)randomWithRange(rng, (double) candidateNodesOutOfTheSP.size(), 0.0));
									wNet.addVnfInstance(randomNode, vnfName, wNet.getVnfType(vnfName).get());
									randomNodeSequence.add(randomNode);
									int hops = wNet.getKShortestWdmPath(1, node, randomNode, Optional.empty()).get(0).size();
									System.out.println(vnfName+" allocated in "+randomNode.getName()+" ("+hops+" hops)");
								}					
							}
						}
						final List<List<WAbstractNetworkElement>> paths = wNet.getKShortestServiceChainInIpLayer(K, node, coreNode, vnfsToTraverse, Optional.empty(), Optional.empty());
						List<WAbstractNetworkElement> ipPath = paths.get(0);
						System.out.println(ipPath);
						scr.addServiceChain(ipPath, totalTrafficInGbpsOfThisServiceInThisNodeGbps);

						//Latency...
						double propagationDelay = 0;
						int numberOfNodes = 0;
						
						if(randomNodeSequence.size() != 0) {
							for(WNode n : randomNodeSequence) {
								
								if(randomNodeSequence.size() == 1) {
									System.out.println("Random sequence size is 1");
									List<WFiber> fiber = wNet.getKShortestWdmPath(1, node, n, Optional.empty()).get(0);
									numberOfNodes += fiber.size();
									System.out.println(fiber);
									System.out.println("Fibers node->n: "+ fiber.size());
									propagationDelay += wNet.getPropagationDelay(fiber);
									fiber = wNet.getKShortestWdmPath(1, n, coreNode, Optional.empty()).get(0);
									numberOfNodes += fiber.size();
									System.out.println(fiber);
									System.out.println("Fibers n->coreNode: "+ fiber.size());
									propagationDelay += wNet.getPropagationDelay(fiber);
									numberOfNodes++; //+= (2-1);
									
								}else {
									if(randomNodeSequence.indexOf(n) == 0) { //first
										List<WFiber> fiber = wNet.getKShortestWdmPath(1, node, n, Optional.empty()).get(0);
										propagationDelay += wNet.getPropagationDelay(fiber);
									}else if (randomNodeSequence.indexOf(n) == randomNodeSequence.size()-1){ //last
										List<WFiber> fiber = wNet.getKShortestWdmPath(1, n, coreNode, Optional.empty()).get(0);
										propagationDelay += wNet.getPropagationDelay(fiber);
									}else {
										int index = randomNodeSequence.indexOf(n);
										List<WFiber> fiber = wNet.getKShortestWdmPath(1, randomNodeSequence.get(index-1), n, Optional.empty()).get(0);
										propagationDelay += wNet.getPropagationDelay(fiber);
									}
									numberOfNodes = randomNodeSequence.size()+2;
								}
								
							}
							double latency = propagationDelay + 2 * 0.1 * numberOfNodes;
							latencies.add(latency);
							System.out.println("latency = propagationDelay + 2 * 0.1 * numberOfNodes");
							System.out.println("latency = "+propagationDelay+" + 2 * 0.1 * "+numberOfNodes);
							System.out.println("Latency in this iteration: "+latency);
							
						}else {
							System.out.println("NO RANDOM NODES...");
							System.out.println("Number of links to traverse (hops): " + firstFiberLinks.size());
							propagationDelay = wNet.getPropagationDelay(firstFiberLinks);
							double latency = propagationDelay + 2 * 0.1 * firstFiberLinks.size();
							latencies.add(latency);
							System.out.println("latency = propagationDelay + 2 * 0.1 * fibers.size()");
							System.out.println("latency = "+propagationDelay+" + 2 * 0.1 * "+firstFiberLinks.size());
							System.out.println("Latency in this iteration: "+latency);
						}

						
					} // else
			}
		}

		/* ############################# */
		System.out.println("############################################################################");
		System.out.println("LIGHTPATHS");
		
		List<WLightpath> lpList = wNet.getLightpaths();
		System.out.println("Number of lightpaths: "+lpList.size());
		for(WLightpath lp : lpList) System.out.println(lp.getA().getName()+" -> "+lp.getB().getName());

		return wNet;
	}
	
	

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public List<Triple<String, String, String>> getParameters() {
		final List<Triple<String, String, String>> param = new LinkedList<Triple<String, String, String>>();
		param.add(Triple.of("Lmax", "5.0", "Maximum latency value"));
		param.add(Triple.of("K", "5", "Number of shortest paths to evaluate"));
		param.add(Triple.of("numServices", "50", "Number of services"));
		param.add(Triple.of("excelFile", "MHTopology_Nodes_Links_95%.xlsx", "Selection of the Excel spreadsheet"));
		param.add(Triple.of("randomSeed", "1", "Seed of the random number generator"));
		
		param.add(Triple.of ("VNFsInMCENb" , "false" , "True if all VNFs will be instantiated in MCENb, false if not."));
		param.add(Triple.of ("concentration" , "false" , "True if concentration nodes will be created, false if not."));
		param.add (Triple.of ("alpha" , "0" , "Factor that determines the number of VNFs in the shortest path"));
		
		return param;
	}

	public double compare(WNode a, WNode b) {
		return new Double(a.getPopulation()).compareTo(new Double(b.getPopulation()));
	}
	
	public double randomWithRange(Random rng , double min, double max) {
		final double range = (max - min);
		return rng.nextDouble() * range + min;
	}

	public double truncate(double value, int places) {
		return new BigDecimal(value).setScale(places, RoundingMode.DOWN).doubleValue();
	}
	
	public WNode getGreatestNode(WNet wNet) {

		List<WNode> nodesList = wNet.getNodes();
		WNode greatestNode = null;
		double maxPopulation = 0;

		for (WNode node : nodesList) {

			if (node.getPopulation() > maxPopulation) {
				maxPopulation = node.getPopulation();
				greatestNode = node.getAsNode();
			}
		}

		return greatestNode;
	}

	public WNode getNearestCoreNode(NetPlan netPlan, WNet wNet, WNode node) {
		int minStepsBetweenNodes = Integer.MAX_VALUE;
		WNode nearestCoreNode = null;
		for (WNode coreNode : wNet.getNodesConnectedToCore()) {
			int currentStepsBetweenNodes = GraphUtils
					.getShortestPath(netPlan.getNodes(), netPlan.getLinks(), node.getNe(), coreNode.getNe(), null)
					.size();
			if (minStepsBetweenNodes > currentStepsBetweenNodes) {
				minStepsBetweenNodes = currentStepsBetweenNodes;
				nearestCoreNode = coreNode;
			}
		}

		return nearestCoreNode;
	}

	public void setServiceVNFSinEndingNode(WNet wNet, WServiceChainRequest service, WNode endingNode) {
		//List<String> serviceVNFS = service.getListVnfTypesToTraverseUpstream();
		List<String> serviceVNFS = service.getSequenceVnfTypes();
		for(String vnfName : serviceVNFS) {
			wNet.addVnfInstance(endingNode, vnfName, wNet.getVnfType(vnfName).get());
		}
	}
	
	public boolean existsDirectLightpath(WNet wNet, String node, String endingNode) {
		
		for(WLightpath lp : wNet.getLightpaths()) if(lp.getA().getName().equals(node) && lp.getB().getName().equals(endingNode)) return true;
		return false;
	}

	private static void writeFile (File file , List<String> rows)
	{
		PrintWriter of = null;
		try 
		{ 
			of = new PrintWriter (new FileWriter (file));
			for (String row : rows)
				of.println(row);
			of.close();
		} catch (Exception e) { e.printStackTrace(); if (of != null) of.close(); throw new Net2PlanException ("File error"); }
		
	}
	
	private static void writeFileInOneLine (File file , List<String> rows)
	{
		PrintWriter of = null;
		try 
		{ 
			of = new PrintWriter (new FileWriter (file));
			for (String row : rows)
				of.print(row);
			of.close();
		} catch (Exception e) { e.printStackTrace(); if (of != null) of.close(); throw new Net2PlanException ("File error"); }
		
	}
	
	private static void prepareAndAddServiceChain(WNet wNet, WServiceChainRequest scr,
			List<WAbstractNetworkElement> finalIpPath, WNode endingNode) {

		double totalTrafficInGbpsOfThisServiceInThisNodeGbps = scr.getCurrentOfferedTrafficInGbps();
		//final WUserService service = wNet.getUserServicesInfo().get(scr.getUserServiceName());
		if (!finalIpPath.contains(null)) {
			//List<String> serviceVNFS = service.getListVnfTypesToTraverseUpstream();
			List<String> serviceVNFS = scr.getSequenceVnfTypes();
			for (String vnfName : serviceVNFS) {
				WVnfInstance vnf = wNet.addVnfInstance(endingNode, vnfName, wNet.getVnfType(vnfName).get());
				finalIpPath.add(vnf);
			}

			System.out.println("#4 Using the custom way...");
			System.out.println(finalIpPath);
			scr.addServiceChain(finalIpPath, totalTrafficInGbpsOfThisServiceInThisNodeGbps);
		}
	}
	
	public List<WNode> getNodesInPath(List<WFiber> fiberList){
		List<WNode> nodeList = new ArrayList<WNode>();
				
		for(WFiber fiber : fiberList) {
			if(fiberList.indexOf(fiber) != (fiberList.size()-1) ) nodeList.add(fiber.getA());
			else nodeList.add(fiber.getB());
		}
		return nodeList;
	}
	
	public List<WNode> getNodesInRangeOutOfTheShortestPath(WNet wNet, WNode originNode, int maxNumberOfHops, List<WNode> nodesInShortestPath){
		List <WNode> validNodes = new ArrayList<WNode>();
		
		for(WNode node : wNet.getNodes()) 
		{
			if( !node.equals(originNode) ) {
				List<List<WFiber>> o2nFiberLists = wNet.getKShortestWdmPath(1, originNode, node, Optional.empty());
				List<WFiber> o2nFiberListK0 = o2nFiberLists.get(0);					
				if( o2nFiberListK0.size() <= maxNumberOfHops && !nodesInShortestPath.contains(node) ) validNodes.add(node);
			}

		}
		
		return validNodes;
	}
	
	
	
	
	
}
