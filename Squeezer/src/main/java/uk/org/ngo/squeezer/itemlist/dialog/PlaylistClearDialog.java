/*
 * Copyright (c) 2020 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.itemlist.dialog;

import androidx.fragment.app.FragmentManager;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseConfirmDialog;

public class PlaylistClearDialog extends BaseConfirmDialog {
    private static final String TAG = PlaylistClearDialog.class.getSimpleName();

    public PlaylistClearDialog(ConfirmDialogListener callback) {
        super(callback);
    }

    @Override
    protected String title() {
        return getString(R.string.CLEAR_PLAYLIST);
    }

    public static PlaylistClearDialog show(FragmentManager fragmentManager, ConfirmDialogListener callback) {
        PlaylistClearDialog dialog = new PlaylistClearDialog(callback);

        dialog.show(fragmentManager, TAG);

        return dialog;
    }
}
