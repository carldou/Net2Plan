/*******************************************************************************
 * Copyright (c) 2017 Pablo Pavon Marino and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the 2-clause BSD License 
 * which accompanies this distribution, and is available at
 * https://opensource.org/licenses/BSD-2-Clause
 *
 * Contributors:
 *     Pablo Pavon Marino and others - initial API and implementation
 *******************************************************************************/
package com.net2plan.gui.plugins.networkDesign.topologyPane;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Map;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

import org.apache.commons.collections15.BidiMap;

import com.net2plan.gui.plugins.GUINetworkDesign;
import com.net2plan.gui.plugins.networkDesign.interfaces.ITopologyCanvas;
import com.net2plan.gui.plugins.networkDesign.topologyPane.jung.osmSupport.OSMException;
import com.net2plan.gui.plugins.networkDesign.topologyPane.jung.state.CanvasOption;
import com.net2plan.gui.plugins.networkDesign.visualizationControl.PickManager;
import com.net2plan.gui.plugins.networkDesign.visualizationControl.PickManager.PickStateInfo;
import com.net2plan.gui.plugins.networkDesign.visualizationControl.VisualizationState;
import com.net2plan.gui.utils.NetworkElementOrFr;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.niw.WNet;
import com.net2plan.utils.Pair;

/**
 * @author Jorge San Emeterio
 * @date 24/04/17
 */
@SuppressWarnings("serial")
public class TopologyTopBar extends JToolBar implements ActionListener
{
    private final GUINetworkDesign callback;
    private final TopologyPanel topologyPanel;
    private final ITopologyCanvas canvas;

    private final JButton btn_load, btn_loadDemand, btn_save, btn_zoomIn, btn_zoomOut, btn_zoomAll, btn_takeSnapshot, btn_reset;
    private final JButton btn_increaseNodeSize, btn_decreaseNodeSize, btn_increaseFontSize, btn_decreaseFontSize;
    private final JButton btn_increaseLinkSize, btn_decreaseLinkSize, btn_tableControlWindow;
    private final JToggleButton btn_niwActive;
    private final JToggleButton btn_showNodeNames, btn_showNodesSite, btn_showLinkIds, btn_showNonConnectedNodes, btn_osmMap, btn_siteMode;
    private final JButton btn_linkStyle;

    public TopologyTopBar(GUINetworkDesign callback, TopologyPanel topologyPanel, ITopologyCanvas canvas)
    {
        super();

        assert callback != null;
        assert topologyPanel != null;
        assert canvas != null;

        this.callback = callback;
        this.topologyPanel = topologyPanel;
        this.canvas = canvas;

        this.setOrientation(JToolBar.HORIZONTAL);
        this.setRollover(true);
        this.setFloatable(false);
        this.setOpaque(false);
        this.setBorderPainted(false);

        btn_load = new JButton();
        btn_load.setToolTipText("Load a network design");
        btn_loadDemand = new JButton();
        btn_loadDemand.setToolTipText("Load a traffic demand set");
        btn_save = new JButton();
        btn_save.setToolTipText("Save current state to a file");
        btn_zoomIn = new JButton();
        btn_zoomIn.setToolTipText("Zoom in");
        btn_zoomOut = new JButton();
        btn_zoomOut.setToolTipText("Zoom out");
        btn_zoomAll = new JButton();
        btn_zoomAll.setToolTipText("Zoom all");
        btn_takeSnapshot = new JButton();
        btn_takeSnapshot.setToolTipText("Take a snapshot of the canvas");
        btn_showNodeNames = new JToggleButton();
        btn_showNodeNames.setToolTipText("Show/hide node names");
        btn_showNodesSite = new JToggleButton();
        btn_showNodesSite.setToolTipText("Show/hide nodes site");
        btn_showLinkIds = new JToggleButton();
        btn_showLinkIds.setToolTipText("Show/hide link utilization, measured as the ratio between the total traffic in the link (including that in protection segments) and total link capacity (including that reserved by protection segments)");
        btn_showNonConnectedNodes = new JToggleButton();
        btn_showNonConnectedNodes.setToolTipText("Show/hide non-connected nodes");
        btn_increaseNodeSize = new JButton();
        btn_increaseNodeSize.setToolTipText("Increase node size");
        btn_decreaseNodeSize = new JButton();
        btn_decreaseNodeSize.setToolTipText("Decrease node size");
        btn_increaseLinkSize = new JButton();
        btn_increaseLinkSize.setToolTipText("Increase link thickness");
        btn_decreaseLinkSize = new JButton();
        btn_decreaseLinkSize.setToolTipText("Decrease link thickness");
        btn_increaseFontSize = new JButton();
        btn_increaseFontSize.setToolTipText("Increase font size");
        btn_decreaseFontSize = new JButton();
        btn_decreaseFontSize.setToolTipText("Decrease font size");
        btn_siteMode = new JToggleButton("Site");
        btn_siteMode.setToolTipText("Toggle on/off node site view.");
        btn_siteMode.setEnabled(false);
        btn_osmMap = new JToggleButton();
        btn_osmMap.setToolTipText("Toggle on/off OSM support. An Internet connection is required for this function.");
        btn_tableControlWindow = new JButton();
        btn_tableControlWindow.setToolTipText("Show the network topology control window.");
        btn_niwActive = new JToggleButton("NIW");
        btn_niwActive.setFont(new Font(new JLabel().getFont ().getFontName(), Font.PLAIN, 20));
        btn_niwActive.setToolTipText("Toggle on/off the NIW (NFV over IP over WDM view)");
        btn_reset = new JButton("Reset");
        btn_reset.setToolTipText("Reset the user interface");
        btn_reset.setMnemonic(KeyEvent.VK_R);
        btn_linkStyle = new JButton();
        btn_linkStyle.setToolTipText("Customize link visualization");

        btn_load.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/loadDesign.png")));
        btn_loadDemand.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/loadDemand.png")));
        btn_save.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/saveDesign.png")));
        btn_showNodeNames.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showNodeName.png")));
        btn_showNodesSite.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showNodeSite.png")));
        btn_showLinkIds.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showLinkUtilization.png")));
        btn_showNonConnectedNodes.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showNonConnectedNodes.png")));
        //btn_whatIfActivated.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showNonConnectedNodes.png")));
        btn_zoomIn.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/zoomIn.png")));
        btn_zoomOut.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/zoomOut.png")));
        btn_zoomAll.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/zoomAll.png")));
        btn_takeSnapshot.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/takeSnapshot.png")));
        btn_increaseNodeSize.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/increaseNode.png")));
        btn_decreaseNodeSize.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/decreaseNode.png")));
        btn_increaseLinkSize.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/increaseLink.png")));
        btn_decreaseLinkSize.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/decreaseLink.png")));
        btn_increaseFontSize.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/increaseFont.png")));
        btn_decreaseFontSize.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/decreaseFont.png")));
        btn_osmMap.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showOSM.png")));
        //btn_niwActive.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/niw.png")));
        btn_tableControlWindow.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/showControl.png")));
        btn_linkStyle.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/linkStyle.png")));

        btn_load.addActionListener(this);
        btn_loadDemand.addActionListener(this);
        btn_save.addActionListener(this);
        btn_showNodeNames.addActionListener(this);
        btn_showNodesSite.addActionListener(this);
        btn_showLinkIds.addActionListener(this);
        btn_showNonConnectedNodes.addActionListener(this);
        btn_zoomIn.addActionListener(this);
        btn_zoomOut.addActionListener(this);
        btn_zoomAll.addActionListener(this);
        btn_takeSnapshot.addActionListener(this);
        btn_niwActive.addActionListener(this);
        btn_reset.addActionListener(this);
        btn_increaseNodeSize.addActionListener(this);
        btn_decreaseNodeSize.addActionListener(this);
        btn_increaseLinkSize.addActionListener(this);
        btn_decreaseLinkSize.addActionListener(this);
        btn_increaseFontSize.addActionListener(this);
        btn_decreaseFontSize.addActionListener(this);
        btn_siteMode.addActionListener(this);
        btn_osmMap.addActionListener(this);
        btn_tableControlWindow.addActionListener(this);
        btn_linkStyle.addActionListener(this);

        this.add(btn_load);
        this.add(btn_loadDemand);
        this.add(btn_save);
        this.add(new JToolBar.Separator());
        this.add(btn_zoomIn);
        this.add(btn_zoomOut);
        this.add(btn_zoomAll);
        this.add(btn_takeSnapshot);
        this.add(new JToolBar.Separator());
        this.add(btn_showNodeNames);
        this.add(btn_showNodesSite);
        this.add(btn_showLinkIds);
        this.add(btn_showNonConnectedNodes);
        this.add(new JToolBar.Separator());
        this.add(btn_increaseNodeSize);
        this.add(btn_decreaseNodeSize);
        this.add(btn_increaseLinkSize);
        this.add(btn_decreaseLinkSize);
        this.add(btn_increaseFontSize);
        this.add(btn_decreaseFontSize);
        this.add(new JToolBar.Separator());
        this.add(btn_linkStyle);
        this.add(Box.createHorizontalGlue());
        this.add(btn_siteMode);
        this.add(btn_osmMap);
        this.add(btn_tableControlWindow);
        this.add(btn_niwActive);
        this.add(btn_reset);

        btn_showNodeNames.setSelected(callback.getVisualizationState().isCanvasShowNodeNames());
        btn_showNodesSite.setSelected(callback.getVisualizationState().isCanvasShowNodeSites());
        btn_showLinkIds.setSelected(callback.getVisualizationState().isCanvasShowLinkLabels());
        btn_showNonConnectedNodes.setSelected(callback.getVisualizationState().isCanvasShowNonConnectedNodes());
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        Object src = e.getSource();
        final VisualizationState vs = callback.getVisualizationState();

        if (src == btn_load)
        {
            topologyPanel.loadDesign();
        } else if (src == btn_loadDemand)
        {
            topologyPanel.loadTrafficDemands();
        } else if (src == btn_save)
        {
            topologyPanel.saveDesign();
        } else if (src == btn_showNodeNames)
        {
            vs.setCanvasShowNodeNames(btn_showNodeNames.isSelected());
            canvas.refresh();
        } else if (src == btn_showNodesSite)
        {
            vs.setCanvasShowNodeSites(btn_showNodesSite.isSelected());
            canvas.refresh();
        } else if (src == btn_showLinkIds)
        {
            vs.setCanvasShowLinkLabels(btn_showLinkIds.isSelected());
            canvas.refresh();
        } else if (src == btn_showNonConnectedNodes)
        {
            vs.setCanvasShowNonConnectedNodes(btn_showNonConnectedNodes.isSelected());
            canvas.refresh();
        } else if (src == btn_takeSnapshot)
        {
            canvas.takeSnapshot();
        } else if (src == btn_zoomIn)
        {
            canvas.zoomIn();
        } else if (src == btn_zoomOut)
        {
            canvas.zoomOut();
        } else if (src == btn_zoomAll)
        {
            canvas.zoomAll();
        } else if (src == btn_reset)
        {
            if (callback.inOnlineSimulationMode()) return;

            int result = JOptionPane.showConfirmDialog(null, "Are you sure you want to reset the design?", "Reset Design", JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION)
            {
            	if (callback.getVisualizationState().isNiwDesignButtonActive())
            		callback.setDesignAndCallWhatIfSomethingModified(WNet.createEmptyDesign(true , true).getNe());
            	else
            		callback.setDesignAndCallWhatIfSomethingModified(new NetPlan());
                Pair<BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> res =
                        vs.suggestCanvasUpdatedVisualizationLayerInfoForNewDesign(new HashSet<>(callback.getDesign().getNetworkLayers()));
                vs.setCanvasLayerVisibilityAndOrder(callback.getDesign(), res.getFirst(), res.getSecond());
                callback.updateVisualizationAfterNewTopology();
                callback.addNetPlanChange();
            }

        } else if (src == btn_osmMap)
        {
            if (btn_osmMap.isSelected())
            {
                try
                {
                    canvas.setState(CanvasOption.OSMState);
                } catch (OSMException ex)
                {
                    btn_osmMap.setSelected(false);
                }
            } else if (!btn_osmMap.isSelected())
            {
                canvas.setState(CanvasOption.ViewState);
            }
        } else if (src == btn_niwActive)
        {
        	vs.setIsNiwDesignButtonActive(btn_niwActive.isSelected());
            Pair<BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> res =
                    vs.suggestCanvasUpdatedVisualizationLayerInfoForNewDesign(new HashSet<>(callback.getDesign().getNetworkLayers()));
            vs.setCanvasLayerVisibilityAndOrder(callback.getDesign(), res.getFirst(), res.getSecond());
            callback.updateVisualizationAfterNewTopology();
        } else if (src == btn_increaseNodeSize)
        {
            callback.getVisualizationState().increaseCanvasNodeSizeAll();
            canvas.refresh();
        } else if (src == btn_decreaseNodeSize)
        {
            callback.getVisualizationState().decreaseCanvasNodeSizeAll();
            canvas.refresh();
        } else if (src == btn_increaseLinkSize)
        {
            callback.getVisualizationState().increaseCanvasLinkSizeAll();
            canvas.refresh();
        } else if (src == btn_decreaseLinkSize)
        {
            callback.getVisualizationState().decreaseCanvasLinkSizeAll();
            canvas.refresh();
        } else if (src == btn_increaseFontSize)
        {
            callback.getVisualizationState().increaseCanvasFontSizeAll();
            canvas.refresh();
        } else if (src == btn_decreaseFontSize)
        {
            final boolean somethingChanged = callback.getVisualizationState().decreaseCanvasFontSizeAll();
            if (somethingChanged) canvas.refresh();
        } else if (src == btn_tableControlWindow)
        {
            callback.showTableControlWindow();
        } else if (src == btn_siteMode)
        {
            if (btn_siteMode.isSelected())
            {
            	final PickStateInfo ps = callback.getPickManager().getCurrentPick(callback.getDesign()).orElse(null); 
                if (ps != null)
                {
                	final NetworkElementOrFr nefr = ps.getMainElement().orElse(null); 
                	if (nefr != null)
                	{
                		if (nefr.isNe())
                			if (nefr.getNe() instanceof Node)
                                canvas.setState(CanvasOption.SiteState, (Node) nefr.getNe());
                	}
                } else
                {
                    btn_siteMode.setSelected(false);
                }
            } else
            {
            	canvas.setState(CanvasOption.ViewState);
            	btn_osmMap.setSelected(false);
                btn_siteMode.setSelected(false);
            }
        } else if (src == btn_linkStyle)
        {
            final LinkStyleSelector lss = new LinkStyleSelector(callback.getVisualizationState());
            lss.setLocationRelativeTo(this);
            lss.setVisible(true);
            canvas.refresh();
        }
    }

    public void update()
    {
        final CanvasOption stateDefinition = canvas.getState();

        if (stateDefinition == null) return;
        switch (stateDefinition)
        {
            case ViewState:
                btn_siteMode.setSelected(false);
                btn_osmMap.setSelected(false);
                break;
            case SiteState:
                btn_siteMode.setSelected(true);
                btn_osmMap.setSelected(false);
                break;
            case OSMState:
                btn_osmMap.setSelected(true);
                btn_siteMode.setSelected(false);
                break;
        }

        final VisualizationState vs = callback.getVisualizationState();
        final PickManager pickManager = callback.getPickManager();
        final PickStateInfo currentPick = pickManager.getCurrentPick(callback.getDesign()).orElse(null);
        if (currentPick != null)
        {
        	final NetworkElementOrFr nefr = currentPick.getMainElement().orElse(null);
        	if (nefr != null)
        	{
        		if (nefr.isNe())
        			if (nefr.getNe() instanceof Node)
                        if (((Node) nefr.getNe()).getSiteName() != null)
                        {
                            btn_siteMode.setEnabled(true);
                            return;
                        }
            }
        } 
        if (canvas.getState() != CanvasOption.SiteState)
            btn_siteMode.setEnabled(false);
    }
}
