/*
Copyright (C) 2014 Keith Conger <keith.conger@gmail.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.thecongers.mtpms;

import android.os.Handler;
import android.os.Message;
import java.lang.ref.WeakReference;

public class StaticHandlerFactory {

    public static StaticHandler create(IStaticHandler ref) {
        return new StaticHandler(ref);
    }

    // This has to be nested.
    static class StaticHandler extends Handler {
        WeakReference<IStaticHandler> weakRef;

        public StaticHandler(IStaticHandler ref) {
            this.weakRef = new WeakReference<IStaticHandler>(ref);
        }

        @Override
        public void handleMessage(Message msg) {
            if (weakRef.get() == null) {
                throw new RuntimeException("Something goes wrong.");
            } else {
                weakRef.get().handleMessage(msg);
            }
        }
    }
}