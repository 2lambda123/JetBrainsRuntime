/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;

/*
 * Represents a key to a specific file on Solaris or Linux
 */
public class FileKey {

    private long st_dev;    // ID of device
    private long st_ino;    // Inode number

    private FileKey() { }

    public static FileKey create(FileDescriptor fd) {
        FileKey fk = new FileKey();
        try {
            fk.init(fd);
        } catch (IOException ioe) {
            throw new Error(ioe);
        }
        return fk;
    }

    public int hashCode() {
        return (int)(st_dev ^ (st_dev >>> 32)) +
               (int)(st_ino ^ (st_ino >>> 32));
    }

    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof FileKey))
            return false;
        FileKey other = (FileKey)obj;
        if ((this.st_dev != other.st_dev) ||
            (this.st_ino != other.st_ino)) {
            return false;
        }
        return true;
    }

    private native void init(FileDescriptor fd) throws IOException;
    private static native void initIDs();

    static {
        initIDs();
    }
}
