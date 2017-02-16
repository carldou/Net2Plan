/*******************************************************************************
 * Copyright (c) 2015 Pablo Pavon Mariño.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Contributors:
 * Pablo Pavon Mariño - initial API and implementation
 ******************************************************************************/


package com.net2plan.gui.utils.topologyPane.jung;

import edu.uci.ics.jung.visualization.control.GraphMousePlugin;

import java.awt.event.*;

import com.net2plan.gui.utils.topologyPane.ITopologyCanvasPlugin;

/**
 * Wrapper class for {@link com.net2plan.gui.utils.topologyPane.ITopologyCanvasPlugin ITopologyCanvasPlugin}
 * to be used as {@link edu.uci.ics.jung.visualization.control.GraphMousePlugin GraphMousePlugin} for JUNG.
 *
 * @author Pablo Pavon-Marino, Jose-Luis Izquierdo-Zaragoza
 * @since 0.3.1
 */
public class GraphMousePluginAdapter implements GraphMousePlugin, MouseListener, MouseMotionListener, MouseWheelListener {
    private final ITopologyCanvasPlugin plugin;

    /**
     * Default constructor.
     *
     * @param plugin Topology canvas plugin
     * @since 0.3.1
     */
    public GraphMousePluginAdapter(ITopologyCanvasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean checkModifiers(MouseEvent e) {
        return plugin.checkModifiers(e);
    }

    @Override
    public int getModifiers() {
        return plugin.getModifiers();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (plugin instanceof MouseListener)
            ((MouseListener) plugin).mouseClicked(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (plugin instanceof MouseMotionListener)
            ((MouseMotionListener) plugin).mouseDragged(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (plugin instanceof MouseListener)
            ((MouseListener) plugin).mouseEntered(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (plugin instanceof MouseListener)
            ((MouseListener) plugin).mouseExited(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (plugin instanceof MouseMotionListener)
            ((MouseMotionListener) plugin).mouseMoved(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (plugin instanceof MouseListener)
            ((MouseListener) plugin).mousePressed(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (plugin instanceof MouseListener)
            ((MouseListener) plugin).mouseReleased(e);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (plugin instanceof MouseWheelListener)
            ((MouseWheelListener) plugin).mouseWheelMoved(e);
    }

    @Override
    public void setModifiers(int modifiers) {
        plugin.setModifiers(modifiers);
    }
}
