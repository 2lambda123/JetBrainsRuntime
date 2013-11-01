/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

/**
 * This package defines JDI events and event processing.
 * An {@link com.sun.jdi.event.Event} is always a member of an
 * {@link com.sun.jdi.event.EventSet}, which
 * is retrieved from the {@link com.sun.jdi.event.EventQueue}.
 * Examples of Events include
 * {@link com.sun.jdi.event.BreakpointEvent "breakpoints events"},
 * {@link com.sun.jdi.event.ThreadStartEvent "thread creation events"} and
 * {@link com.sun.jdi.event.VMDeathEvent "virtual machine death event"}.
 *  With the exception
 * of termination events, all events received must be requested with an
 * {@link com.sun.jdi.request.EventRequest "EventRequest"}.  The
 * {@link com.sun.jdi.request} package defines event requests and event
 * request management.
 * <p>
 * Methods may be added to the interfaces in the JDI packages in future
 * releases. Existing packages may be renamed if the JDI becomes a standard
 * extension.
 */

@jdk.Exported
package com.sun.jdi.event;
