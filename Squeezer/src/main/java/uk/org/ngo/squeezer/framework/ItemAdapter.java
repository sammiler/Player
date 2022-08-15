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

import android.os.Parcelable;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.util.Reflection;


/**
 * A generic class for an adapter to list items of a particular SqueezeServer data type. The data
 * type is defined by the generic type argument, and must be an extension of {@link Item}.
 * <p>
 * Extend this and {@link VH}, to display a list of items
 *
 * @param <T> Denotes the class of the items this class should list
 *
 * @author Kurt Aaholst
 * @see ItemViewHolder
 */
public abstract class ItemAdapter<VH extends ItemViewHolder<T>, T extends Item> extends RecyclerView.Adapter<VH> {

    /**
     * Activity which hosts this adapter
     */
    private final ItemListActivity activity;

    /**
     * List of items, possibly headed with an empty item.
     * <p>
     * As the items are received from SqueezeServer they will be inserted in the list.
     */
    private int count;

    private final SparseArray<T[]> pages = new SparseArray<>();

    /**
     * This is set if the list shall start with an empty item.
     */
    private final boolean mEmptyItem;

    /**
     * Text to display before the items are received from SqueezeServer
     */
    private final String loadingText;

    /**
     * Number of elements to be fetched at a time
     */
    private final int pageSize;

    /**
     * Creates a new adapter. Initially the item list is populated with items displaying the
     * localized "loading" text. Call {@link #update(int, int, List)} as items arrives from
     * SqueezeServer.
     *
     * @param activity The {@link ItemListActivity} which hosts this adapter
     * @param emptyItem If set the list of items shall start with an empty item
     */
    public ItemAdapter(ItemListActivity activity, boolean emptyItem) {
        this.activity = activity;
        mEmptyItem = emptyItem;
        loadingText = getActivity().getString(R.string.loading_text);
        pageSize = getActivity().getResources().getInteger(R.integer.PageSize);
        pages.clear();
    }

    /**
     * Calls {@link #(ItemListActivity, boolean)}, with emptyItem = false
     */
    public ItemAdapter(ItemListActivity activity) {
        this(activity, false);
    }

    private int pageNumber(int position) {
        return position / pageSize;
    }

    /**
     * Removes all items from this adapter leaving it empty.
     */
    public void clear() {
        count = (mEmptyItem ? 1 : 0);
        pages.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return createViewHolder(view);
    }

    public abstract VH createViewHolder(View view);

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        T item = getItem(position);
        if (item != null)
            holder.bindView(item);
        else
            holder.bindView((position == 0 && mEmptyItem ? "" : loadingText));
    }

    @Override
    public int getItemViewType(int position) {
        return getItemViewType(getItem(position));
    }

    protected abstract int getItemViewType(T item);

    protected ItemListActivity getActivity() {
        return activity;
    }

    @Override
    public int getItemCount() {
        return count;
    }

    private T[] getPage(int position) {
        int pageNumber = pageNumber(position);
        T[] page = pages.get(pageNumber);
        if (page == null) {
            pages.put(pageNumber, page = arrayInstance(pageSize));
        }
        return page;
    }

    private void setItems(int start, List<T> items) {
        T[] page = getPage(start);
        int offset = start % pageSize;
        for (T item : items) {
            if (offset >= pageSize) {
                start += offset;
                page = getPage(start);
                offset = 0;
            }
            page[offset++] = item;
        }
    }

    public T getItem(int position) {
        T item = getPage(position)[position % pageSize];
        if (item == null) {
            if (mEmptyItem) {
                position--;
            }
            getActivity().maybeOrderPage(pageNumber(position) * pageSize);
        }
        return item;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Called when the number of items in the list changes. The default implementation is empty.
     */
    protected void onCountUpdated() {
    }

    /**
     * Update the contents of the items in this list.
     * <p>
     * The size of the list of items is automatically adjusted if necessary, to obey the given
     * parameters.
     *
     * @param count Number of items as reported by SqueezeServer.
     * @param start The start position of items in this update.
     * @param items New items to insert in the main list
     */
    public void update(int count, int start, List<T> items) {
        int offset = (mEmptyItem ? 1 : 0);
        count += offset;
        start += offset;
        boolean countUpdated = (count == 0 || count != getItemCount());

        setItems(start, items);
        if (countUpdated) {
            this.count = count;
            onCountUpdated();
            notifyDataSetChanged();
        } else {
            notifyItemRangeChanged(start, items.size());
        }
    }

    /**
     * Move the item at the specified position to the new position and notify the change.
     */
    public void moveItem(int fromPosition, int toPosition) {
        T item = getItem(fromPosition);
        remove(fromPosition);
        insert(toPosition, item);
        notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * Remove the item at the specified position, update the count and notify the change.
     */
    public void removeItem(int position) {
        remove(position);
        count--;
        onCountUpdated();
        notifyItemRemoved(position);
    }

    /**
     * Insert an item at the specified position, update the count and notify the change.
     */
    public void insertItem(int position, T item) {
        insert(position, item);
        count++;
        onCountUpdated();
        notifyItemInserted(position);
    }

    private void remove(int position) {
        T[] page = getPage(position);
        int offset = position % pageSize;
        while (position++ <= count) {
            if (offset == pageSize - 1) {
                T[] nextPage = getPage(position);
                page[offset] = nextPage[0];
                offset = 0;
                page = nextPage;
            } else {
                page[offset] = page[offset+1];
                offset++;
            }
        }

    }

    private void insert(int position, T item) {
        int n = count;
        T[] page = getPage(n);
        int offset = n % pageSize;
        while (n-- > position) {
            if (offset == 0) {
                T[] nextPage = getPage(n);
                offset = pageSize - 1;
                page[0] = nextPage[offset];
                page = nextPage;
            } else {
                page[offset] = page[offset-1];
                offset--;
            }
        }
        page[offset] = item;
    }

    private T[] arrayInstance(int size) {
        return getItemCreator().newArray(size);
    }

    private Class<T> _itemClass;

    private Parcelable.Creator<T> _itemCreator;
    /**
     * @return The generic argument of the implementation
     */
    @SuppressWarnings("unchecked")
    public Class<T> getItemClass() {
        if (_itemClass == null) {
            _itemClass = (Class<T>) Reflection.getGenericClass(getClass(), ItemAdapter.class,
                    1);
            if (_itemClass == null) {
                throw new RuntimeException("Could not read generic argument for: " + getClass());
            }
        }
        return _itemClass;
    }

    /**
     * @return the creator for the current {@link Item} implementation
     */
    @SuppressWarnings("unchecked")
    public Parcelable.Creator<T> getItemCreator() {
        if (_itemCreator == null) {
            Field field;
            try {
                field = getItemClass().getField("CREATOR");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            try {
                _itemCreator = (Parcelable.Creator<T>) field.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return _itemCreator;
    }

}
