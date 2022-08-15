/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.framework;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.widget.SquareImageView;

/**
 * Represents the view hierarchy for a single {@link Item} subclass, suitable for displaying in a
 * {@link ItemListActivity}.
 * <p>
 * This class supports views that have a {@link TextView} to display the primary information about
 * the {@link Item} and can optionally enable additional views.  The layout is defined in {@code
 * res/layout/list_item.xml}. <ul> <li>A {@link SquareImageView} suitable for displaying icons</li>
 * <li>A second, smaller {@link TextView} for additional item information</li> <li>A {@link
 * Button} that shows a disclosure triangle for a context menu</li> </ul> The view can
 * display an item in one of two states.  The primary state is when the data to be inserted in to
 * the view is known, and represented by a complete {@link Item} subclass. The loading state is when
 * the data type is known, but has not been fetched from the server yet.
 * <p>
 * To customise the view's display create an int of {@link ViewParam} and pass it to
 * {@link #setViewParams(int)} or {@link #setLoadingViewParams(int)} depending on whether
 * you want to change the layout of the view in its primary state or the loading state. For example,
 * if the primary state should show a context button you may not want to show that button while
 * waiting for data to arrive.
 * <p>
 * Override {@link #bindView(Item)} and {@link #bindView(String)} to
 * control how data from the item is inserted in to the view.
 *
 * @param <T> the Item subclass this view represents.
 */
public class ViewParamItemView<T extends Item> extends ItemViewHolder<T> {

    @IntDef(flag=true, value={
            VIEW_PARAM_ICON, VIEW_PARAM_TWO_LINE, VIEW_PARAM_CONTEXT_BUTTON
    })
    @Retention(RetentionPolicy.SOURCE)
    /* Parameters that control which additional views will be enabled in the item view. */
    public @interface ViewParam {}
    /** Adds a {@link SquareImageView} for displaying artwork or other iconography. */
    public static final int VIEW_PARAM_ICON = 1;
    /** Adds a second line for detail information ({@code R.id.text2}). */
    public static final int VIEW_PARAM_TWO_LINE = 1 << 1;
    /** Adds a button, with click handler, to display the context menu. */
    public static final int VIEW_PARAM_CONTEXT_BUTTON = 1 << 2;

    /**
     * View parameters for a filled-in view.  One primary line with context button.
     */
    @ViewParam private int itemViewParams = VIEW_PARAM_CONTEXT_BUTTON;

    /**
     * View parameters for a view that is loading data.  Primary line only.
     */
    @ViewParam private int loadingViewParams = 0;

    public final ImageView icon;
    public final TextView text1;
    public final TextView text2;

    public final View contextMenuButtonHolder;
    public Button contextMenuButton;
    public ProgressBar contextMenuLoading;
    protected CheckBox contextMenuCheckbox;
    protected RadioButton contextMenuRadio;

    private @ViewParam int viewParams;

    public ViewParamItemView(@NonNull BaseActivity activity, @NonNull View view) {
        super(activity, view);
        text1 = view.findViewById(R.id.text1);
        text2 = view.findViewById(R.id.text2);
        icon = view.findViewById(R.id.icon);
        contextMenuButtonHolder = view.findViewById(R.id.context_menu);
        if (contextMenuButtonHolder!= null) {
            contextMenuButton = contextMenuButtonHolder.findViewById(R.id.context_menu_button);
            contextMenuLoading = contextMenuButtonHolder.findViewById(R.id.loading_progress);
            contextMenuCheckbox = contextMenuButtonHolder.findViewById(R.id.checkbox);
            contextMenuRadio = contextMenuButtonHolder.findViewById(R.id.radio);
        }
    }

    private void setViewParams(@ViewParam int viewParams) {
        icon.setVisibility((viewParams & VIEW_PARAM_ICON) != 0 ? View.VISIBLE : View.GONE);
        text2.setVisibility((viewParams & VIEW_PARAM_TWO_LINE) != 0 ? View.VISIBLE : View.GONE);

        if (contextMenuButtonHolder != null) {
            contextMenuButtonHolder.setVisibility(
                    (viewParams & VIEW_PARAM_CONTEXT_BUTTON) != 0 ? View.VISIBLE : View.GONE);
        }
        this.viewParams = viewParams;
    }


    /**
     * Set the view parameters to use for the view when data is loaded.
     */
    protected void setItemViewParams(@ViewParam int viewParams) {
        itemViewParams = viewParams;
    }

    /**
     * Set the view parameters to use for the view while data is being loaded.
     */
    protected void setLoadingViewParams(@ViewParam int viewParams) {
        loadingViewParams = viewParams;
    }

    @Override
    public ItemListActivity getActivity() {
        return (ItemListActivity) super.getActivity();
    }

    /**
     * Binds the item's name to {@link #text1}, and set up the context menu.
     */
    @Override
    public void bindView(T item) {
        text1.setText(item.getName());

        if (contextMenuButton!= null) {
            contextMenuButton.setOnClickListener(v -> showContextMenu(item));
        }

        if (itemViewParams != viewParams) {
            setViewParams(itemViewParams);
        }
    }

    /**
     * Binds the text to {@link #text1}.
     */
    @Override
    public void bindView(String text) {
        text1.setText(text);
        if (loadingViewParams != viewParams) {
            setViewParams(loadingViewParams);
        }
    }

    /**
     * Creates the context menu.
     * <p>
     * The default implementation is empty.
     * <p>
     * Subclasses with a context menu should override this method, create a
     * {@link android.widget.PopupMenu} or a {@link android.app.Dialog} then
     * inflate their context menu and show it.
     *
     */
    public void showContextMenu(T item) {
    }
}
