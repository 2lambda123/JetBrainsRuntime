/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;

/**
 *
 * @author Thomas Wuerthinger
 */
public class PredSuccAction extends AbstractAction {


    public PredSuccAction(boolean selected) {
        putValue(Action.SELECTED_KEY, selected);
        putValue(AbstractAction.SMALL_ICON, new ImageIcon(ImageUtilities.loadImage(iconResource())));
        putValue(Action.SHORT_DESCRIPTION, "Show neighboring nodes of fully visible nodes semi-transparent");
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            boolean selected = (boolean)getValue(SELECTED_KEY);
            editor.getModel().setShowNodeHull(selected);
        }
    }

    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/predsucc.gif";
    }
}
