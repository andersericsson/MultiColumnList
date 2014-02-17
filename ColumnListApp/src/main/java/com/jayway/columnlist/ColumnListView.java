package com.jayway.columnlist;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A multi column list
 */
public class ColumnListView extends AdapterView<ListAdapter> {

    // Touch states
    private enum TouchState {
        RESTING, PRESSED, SCROLLING
    }

    // Holder class for the data of items of the list
    // All the data is provided by the adapter
    private static class Item {

        // Position in the adapter for this item
        private int mPosition;

        // The view of the item
        private View mView;

        // The id for this item
        private long mId;
    }

    // Represents the visible part of a column
    private static class Column {
        // The items that makes up the column
        final ArrayList<Item> mItems = new ArrayList<Item>();

        // The left position of the column
        int mLeft;

        // The top coordinate of the column
        int mTop;

        // The bottom coordinate of the column
        int mBottom;

        // The list of item positions that used to be above the current
        // list of items
        final ArrayList<Integer> mPreviousItems = new ArrayList<Integer>();
    }


    // The adapter that contains the data
    private ListAdapter mAdapter;

    // The list of columns
    final private ArrayList<Column> mColumns = new ArrayList<Column>();

    // An observer that is registered on the adapter to be able to react to changes in the data
    private DataSetObserver mDataSetObserver;

    // Cache of item views
    final private HashMap<Integer, ArrayList<View>> mItemViewCache = new HashMap<Integer, ArrayList<View>>();

    // The padding between columns
    private int mPadding;

    // The width of a column
    private int mColumnWidth;

    // The current touch status
    private TouchState mTouchState = TouchState.RESTING;

    // The length needed to move a touch for it to be a scroll
    private final int mTouchSlop;

    // The x-coordinate where the touch started
    private int mTouchDownX;

    // The y-coordinate where the touch started
    private int mTouchDownY;

    // The first column top position when the touch started
    private int mListTopAtTouchStart;

    // A velocity tracker used to calculate the velocity of the fling
    private VelocityTracker mVelocityTracker;

    // The touched item, if any
    private Item mTouchedItem;

    // The number of columns
    private int mNumberOfColumns;

    // The damping while flinging, higher number -> more damping
    private float mFlingDamping;

    // The spring when snapping, higher number -> faster snap
    private float mSnapSpring;

    // The damping when snapping, calculated based on the snap spring
    private float mSnapDamping;

    // The amount of resistance when dragging outside of limits
    private float mRubberbandFactor;

    // True if the views should be reloaded next layout pass
    private boolean mReloadViews;

    // True if overscoll is allowed
    private boolean mOverscroll;



    public ColumnListView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        readAttrs(context, attrs);

        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        createColumns(mNumberOfColumns);
    }

    private void readAttrs(final Context context, final AttributeSet attrs) {
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ColumnListView);

        mNumberOfColumns = attributes.getInt(R.styleable.ColumnListView_columns, 2);
        mPadding = (int) attributes.getDimension(R.styleable.ColumnListView_column_padding, 0);
        mOverscroll = attributes.getBoolean(R.styleable.ColumnListView_overscroll, true);
        mFlingDamping = attributes.getFloat(R.styleable.ColumnListView_fling_damping, 1.5f);
        mSnapSpring = attributes.getInt(R.styleable.ColumnListView_snap_spring, 100);
        mSnapDamping = 2 * FloatMath.sqrt(mSnapSpring);
        mRubberbandFactor = attributes.getFloat(R.styleable.ColumnListView_rubberband_factor, 0.4f);

        attributes.recycle();
    }

    private void createColumns(int numberOfColumns) {
        for (int i = 0; i < numberOfColumns; i++) {
            Column column = new Column();
            mColumns.add(column);
        }
    }

    @Override
    public ListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setAdapter(final ListAdapter adapter) {
        if (mAdapter != null) {
            // if we had an adapter before, unregister the DataSetObserver on it
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }

        clearAllData();

        mAdapter = adapter;

        if (mAdapter != null) {
            ensureDataSetObserverIsCreated();
            mAdapter.registerDataSetObserver(mDataSetObserver);
        }
        requestLayout();

    }

    private void ensureDataSetObserverIsCreated() {
        if (mDataSetObserver == null) {
            mDataSetObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    mReloadViews = true;
                    requestLayout();
                }

                @Override
                public void onInvalidated() {
                    clearAllData();
                    requestLayout();
                }
            };
        }
    }

    private void clearAllData() {
        clearAllViews();
        for (Column column : mColumns) {
            column.mItems.clear();
            column.mTop = 0;
            column.mBottom = 0;
            column.mPreviousItems.clear();
        }
        mItemViewCache.clear();
    }

    private void clearAllViews() {
        for (Column column : mColumns) {
            for (Item item : column.mItems) {
                removeItemView(item);
            }
        }
        removeAllViewsInLayout();
    }

    @Override
    public View getSelectedView() {
        return null;
    }

    @Override
    public void setSelection(final int position) {
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateColumnDimensions(w);
    }

    private void updateColumnDimensions(int width) {
        width -= getPaddingLeft() + getPaddingRight();
        mColumnWidth = (width - (mColumns.size() + 1) * mPadding) / mColumns.size();
        int columnLeft = mPadding;
        for (Column column : mColumns) {
            column.mLeft = columnLeft + getPaddingLeft();
            columnLeft += mColumnWidth + mPadding;
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateColumnDimensions(getMeasuredWidth());
        for (Column column : mColumns) {
            for (Item item : column.mItems) {
                if (item.mView != null) {
                    measureView(item.mView);
                }
            }
        }
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        if (mAdapter == null) {
            return;
        }
        if (mReloadViews) {
            mReloadViews = false;
            reloadViews();
        }
        fillList();
    }

    private void reloadViews() {
        for (Column column : mColumns) {
            int top = column.mTop + mPadding;
            for (Item item : column.mItems) {
                // remove the old view
                removeItemView(item);

                // load the new one
                item.mView = getView(item.mPosition);

                // add, measure and layout the new view
                addViewToLayout(item.mView);
                measureView(item.mView);
                layoutItem(column, item, top);

                top += item.mView.getHeight() + mPadding;
            }
        }
    }


    private void fillList() {
        fillListDown();
        fillListUp();
    }

    private void fillListDown() {
        int nextPosition = getLastVisiblePosition() + 1;
        Column column = getNextColumnDown();
        while (column != null && nextPosition < mAdapter.getCount()) {
            Item item = getItemFromAdapter(nextPosition);
            addItemToColumnDown(column, item);
            column = getNextColumnDown();
            nextPosition++;
        }
    }

    private void fillListUp() {
        Column column = getNextColumnUp();
        int nextPosition = -1;
        if (column != null && !column.mPreviousItems.isEmpty()) {
            nextPosition = column.mPreviousItems.remove(0);
        }
        while (column != null &&  nextPosition >= 0) {
            Item item = getItemFromAdapter(nextPosition);
            addItemToColumnUp(column, item);
            column = getNextColumnUp();

            if (column != null && !column.mPreviousItems.isEmpty()) {
                nextPosition = column.mPreviousItems.remove(0);
            } else {
                nextPosition = -1;
            }
        }
    }

    private Column getNextColumnDown() {
        Column nextColumn = null;
        int highestBottom = getHeight();
        for (Column column : mColumns) {
            if (column.mBottom < highestBottom) {
                highestBottom = column.mBottom;
                nextColumn = column;
            }
        }
        return nextColumn;
    }

    private Column getNextColumnUp() {
        Column nextColumn = null;
        int lowestTop = 0;
        for (Column column : mColumns) {
            if (column.mTop > lowestTop) {
                lowestTop = column.mBottom;
                nextColumn = column;
            }
        }
        return nextColumn;
    }

    @Override
    public int getLastVisiblePosition() {
        int lastPosition = -1;
        for (Column column : mColumns) {
            if (!column.mItems.isEmpty()) {
                int lastPositionInColumn = column.mItems.get(column.mItems.size() - 1).mPosition;
                if (lastPositionInColumn > lastPosition) {
                    lastPosition = lastPositionInColumn;
                }
            }
        }
        return lastPosition;
    }

    @Override
    public int getFirstVisiblePosition() {
        int firstPosition = Integer.MAX_VALUE;
        for (Column column : mColumns) {
            int firstPositionInColumn = column.mItems.get(0).mPosition;
            if (firstPositionInColumn < firstPosition) {
                firstPosition = firstPositionInColumn;
            }
        }
        return firstPosition;
    }

    private Item getItemFromAdapter(final int position) {
        Item item = new Item();
        item.mView = getView(position);
        item.mPosition = position;
        item.mId = mAdapter.getItemId(position);
        return item;
    }

    private View getView(final int position) {
        int viewType = mAdapter.getItemViewType(position);
        View cachedView = getViewFromCache(viewType);
        return mAdapter.getView(position, cachedView, this);
    }

    private void addItemToColumnDown(final Column column, final Item item) {
        addViewToLayout(item.mView);
        measureView(item.mView);
        int height = item.mView.getMeasuredHeight();
        column.mItems.add(item);

        int top = column.mBottom + mPadding;
        layoutItem(column, item, top);

        column.mBottom += height + mPadding;
    }

    private void addItemToColumnUp(final Column column, final Item item) {
        addViewToLayout(item.mView);
        measureView(item.mView);
        int height = item.mView.getMeasuredHeight();
        column.mItems.add(0, item);

        column.mTop -= height + mPadding;
        if (column == mColumns.get(0)) {
            mListTopAtTouchStart -= height + mPadding;
        }
        int top = column.mTop + mPadding;
        layoutItem(column, item, top);
    }

    private void layoutItem(final Column column, final Item item, final int top) {
        item.mView.layout(column.mLeft, top, column.mLeft + mColumnWidth, top + item.mView.getMeasuredHeight());
    }

    private void addViewToLayout(final View view) {
        LayoutParams params = view.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        }
        addViewInLayout(view, -1, params, true);
    }

    private void measureView(final View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();

        int width = mColumnWidth;
        int widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);

        int height = params.height;
        int heightMeasureSpec;
        if (height > 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        } else {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }

        view.measure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (event.getActionIndex() > 0) {
            return false;
        }

        boolean handled;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handled = startTouch(event);
                break;
            case MotionEvent.ACTION_MOVE:
                handled = handleTouchMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handled = handleTouchUp(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                handled = false;
                break;
            default:
                handled = endTouch();
                break;
        }
        return handled;
    }

    private boolean startTouch(MotionEvent event) {
        mTouchState = TouchState.PRESSED;
        mTouchDownX = (int) event.getX();
        mTouchDownY = (int) event.getY();
        mListTopAtTouchStart = getListTop();
        mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(event);
        mTouchedItem = getTouchedItem((int) event.getX(), (int) event.getY());

        // post a runnable that will set the touched view to pressed
        // it's done after a while since this might still be a scroll
        postDelayed(mSetPressedRunnable, ViewConfiguration.getScrollDefaultDelay());
        return true;
    }

    // Runnable that sets pressed state to the touched item
    final private Runnable mSetPressedRunnable = new Runnable() {
        @Override
        public void run() {
            if (mTouchedItem != null && mTouchedItem.mView != null) {
                mTouchedItem.mView.setPressed(true);
            }
        }
    };

    private Item getTouchedItem(int x, int y) {
        for (Column column : mColumns) {
            if (x > column.mLeft && x < column.mLeft + mColumnWidth) {
                for (Item item : column.mItems) {
                    View view = item.mView;
                    if (view.getTop() < y && view.getBottom() > y) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private boolean handleTouchMove(MotionEvent event) {
        mVelocityTracker.addMovement(event);
        if (mTouchState == TouchState.PRESSED && hasMovedFarEnoughForScroll(event)) {
            startScrolling(event);
        } else if (mTouchState == TouchState.SCROLLING) {
            handleTouchScroll(event);
        }
        return true;
    }

    private boolean hasMovedFarEnoughForScroll(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if ((mTouchDownX - mTouchSlop < x && x < mTouchDownX + mTouchSlop) && (mTouchDownY - mTouchSlop < y && y < mTouchDownY + mTouchSlop)) {
            return false;
        }
        return true;
    }

    private void startScrolling(MotionEvent event) {
        removeCallbacks(mSetPressedRunnable);
        if (mTouchedItem != null && mTouchedItem.mView != null) {
            mTouchedItem.mView.setPressed(false);
        }
        mTouchDownX = (int) event.getX();
        mTouchDownY = (int) event.getY();
        mTouchState = TouchState.SCROLLING;
    }

    private void handleTouchScroll(MotionEvent event) {
        int listTop = (int) (mListTopAtTouchStart + (event.getY() - mTouchDownY));
        scrollListTo(applyRubberBand(listTop));
    }

    private int applyRubberBand(final int pos) {
        float rubberbandFactor = mOverscroll ? mRubberbandFactor : 0;
        if (isFirstItemShowing()) {
            int topRubberbandPos = getTopSnapPos();
            if (pos > topRubberbandPos) {
                return (int) (topRubberbandPos + (pos - topRubberbandPos) * rubberbandFactor);
            }
        }

        if (isLastItemShowing()) {
            int bottomRubberbandPos = getBottomSnapPos();
            if (pos < bottomRubberbandPos) {
                return (int) (bottomRubberbandPos + (pos - bottomRubberbandPos) * rubberbandFactor);
            }
        }

        return pos;
    }

    private void scrollListTo(final int listTop) {
        offsetListTo(listTop);
        removeNonVisibleViews();
        fillList();
        invalidate();
    }

    private void offsetListTo(int pos) {
        int delta = pos - mColumns.get(0).mTop;
        for (Column column : mColumns) {
            column.mTop += delta;
            column.mBottom += delta;
            for (Item item : column.mItems) {
                item.mView.offsetTopAndBottom(delta);
            }
        }
    }

    private void removeNonVisibleViews() {
        for (Column column : mColumns) {
            while (!isTopItemVisible(column) && !isLastItemShowing() && column.mItems.size() > 1) {
                removeTopItem(column);
            }

            while (!isBottomItemVisible(column) && !isFirstItemShowing() && column.mItems.size() > 1) {
                removeBottomItem(column);
            }
        }
    }

    private boolean isTopItemVisible(final Column column) {
        return column.mItems.get(0).mView.getBottom() >= 0;
    }

    private boolean isBottomItemVisible(final Column column) {
        return column.mItems.get(column.mItems.size() - 1).mView.getTop() <= getHeight() - getPaddingBottom();
    }

    private void removeTopItem(final Column column) {
        Item item = column.mItems.remove(0);
        column.mTop += item.mView.getHeight() + mPadding;
        if (column == mColumns.get(0)) {
            mListTopAtTouchStart += item.mView.getHeight() + mPadding;
        }
        column.mPreviousItems.add(0, item.mPosition);
        removeItemView(item);
    }

    private void removeBottomItem(final Column column) {
        Item item = column.mItems.remove(column.mItems.size() - 1);
        column.mBottom -= item.mView.getHeight() + mPadding;
        removeItemView(item);
    }

    private void removeItemView(final Item item) {
        removeViewInLayout(item.mView);
        addItemViewToCache(item);
        item.mView = null;
    }

    private boolean isLastItemShowing() {
        for (Column column : mColumns) {
            if (column.mItems.get(column.mItems.size() - 1).mPosition == mAdapter.getCount() - 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstItemShowing() {
        for (Column column : mColumns) {
            if (column.mItems.get(0).mPosition == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean handleTouchUp(MotionEvent event) {
        if (mTouchState == TouchState.PRESSED && mTouchedItem != null) {
            handleItemClick(mTouchedItem);
        }
        endTouch();
        return true;
    }

    private void handleItemClick(Item item) {
        OnItemClickListener onItemClickListener = getOnItemClickListener();
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(this, item.mView, item.mPosition, item.mId);
        }

        // remove any runnable that will set pressed state
        removeCallbacks(mSetPressedRunnable);

        if (item != null && item.mView != null) {
            if (item.mView.isPressed()) {
                // if it was already in pressed state, set it to not pressed
                item.mView.setPressed(false);
            } else {
                // if it was not in pressed state, set it to pressed and
                // post a runnable that resets it after a short duration
                // this way a click is always visible to the user
                item.mView.setPressed(true);
                final View view = item.mView;
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setPressed(false);
                    }
                }, ViewConfiguration.getPressedStateDuration());
            }
        }
    }

    private boolean endTouch() {
        mVelocityTracker.computeCurrentVelocity(1000);
        float velocity = mVelocityTracker.getYVelocity();
        new FlingRunnable(velocity).start();

        mVelocityTracker.recycle();
        mVelocityTracker = null;

        mTouchState = TouchState.RESTING;
        return true;
    }

    private void addItemViewToCache(final Item item) {
        int itemViewType = mAdapter.getItemViewType(item.mPosition);
        ArrayList<View> viewCacheForType = mItemViewCache.get(itemViewType);
        if (viewCacheForType == null) {
            viewCacheForType = new ArrayList<View>();
            mItemViewCache.put(itemViewType, viewCacheForType);
        }
        viewCacheForType.add(item.mView);
    }

    private View getViewFromCache(final int itemViewType) {
        ArrayList<View> viewCacheForType = mItemViewCache.get(itemViewType);
        if (viewCacheForType != null && !viewCacheForType.isEmpty()) {
            return viewCacheForType.remove(0);
        }
        return null;
    }


    private int getTopSnapPos() {
        return getPaddingTop();
    }

    private int getBottomSnapPos() {
        int listHeight = getListHeight();
        if (listHeight < getHeight() - getPaddingBottom()) {
            return mPadding;
        }
        return getHeight() - getPaddingBottom() - getListHeight();
    }


    private int getListHeight() {
        int listTop = getListTop();
        int listHeight = 0;
        for (Column column : mColumns) {
            int columnHeight = column.mBottom - listTop;
            if (columnHeight > listHeight) {
                listHeight = columnHeight;
            }
        }
        return listHeight + mPadding;
    }

    private int getListTop() {
        return mColumns.get(0).mTop;
    }

    private class FlingRunnable implements Runnable {

        // The minimum speed of a fling move to start a fling scroll
        public static final int SPEED_THRESHOLD = 200;

        // The maximum time between frames in milliseconds
        public static final int MAX_FRAME_DELAY = 50;

        // The wanted time between frames in milliseconds
        public static final int WANTED_FRAME_DELAY = 10;

        // The minimum amount of acceleration to keep flinging
        public static final int ACCELERATION_THERSHOLD = 20;

        // The current velocity of the fling
        private float mVelocity;

        // The last time the fling was updated
        private long mLastTime;

        // The point to snap the top of the list to
        private int mSnapPoint;

        // True if we should snap the top of the list to the snap point
        private boolean mSnapping;

        public FlingRunnable(float velocity) {
            if (Math.abs(velocity) > SPEED_THRESHOLD) {
                mVelocity = velocity;
            } else {
                mVelocity = 0;
            }
            mLastTime = AnimationUtils.currentAnimationTimeMillis();
        }

        public void start() {
            removeCallbacks(this);
            scheduleNewFrame();
        }

        @Override
        public void run() {
            if (mTouchState != TouchState.RESTING) {
                // If the user is touching the list, then we just abort
                return;
            }

            int listTop = getListTop();

            if (!mSnapping) {
                snapIfNeeded(listTop);
            }

            float acceleration = getAcceleration();
            float dt = getDeltaTAndSaveCurrentTime();
            mVelocity += acceleration * dt;
            int deltaPos = (int) (mVelocity * dt);

            // If we are snapping and we're not at the snap point, then we (also) decrease the
            // distance to the snap point by one pixel to make sure we reach the snap point
            if (mSnapping && getListTop() + deltaPos != mSnapPoint) {
                if (getListTop() + deltaPos < mSnapPoint) {
                    deltaPos++;
                } else {
                    deltaPos--;
                }
            }

            if (!mOverscroll) {
                // Overscroll is disabled, check if the new position makes us want to snap
                if (!mSnapping) {
                    snapIfNeeded(listTop + deltaPos);
                }

                // if we should snap, reset acceleration and velocity and re-calculate the
                // delta pos so that we position the list exactly at the snap position
                if (mSnapping) {
                    mVelocity = 0;
                    acceleration = 0;
                    deltaPos = mSnapPoint - listTop;
                }
            }

            scrollListTo(listTop + deltaPos);

            if (Math.abs(acceleration) > ACCELERATION_THERSHOLD) {
                scheduleNewFrame();
            }

        }

        private float getAcceleration() {
            // the damping part of the acceleration (directed against the velocity)
            float acceleration = (mSnapping ? mSnapDamping : mFlingDamping) * -mVelocity;

            if (mSnapping) {
                int distanceToSnapPoint = mSnapPoint - getListTop();
                // the spring part of the acceleration (directed towards the snap point)
                acceleration += mSnapSpring * distanceToSnapPoint;
            }
            return acceleration;
        }

        private void snapIfNeeded(int listTop) {
            if (isFirstItemShowing()) {
                mSnapPoint = getTopSnapPos();
                if (listTop > mSnapPoint && mVelocity >= 0) {
                    // the top row is the first row and...
                    // the top of the list is farther down than the snap pos and...
                    // the velocity is directed downward
                    mSnapping = true;
                }
            }

            if (isLastItemShowing()) {
                mSnapPoint = getBottomSnapPos();
                if (listTop < mSnapPoint && mVelocity <= 0) {
                    // the bottom row is the last row and ...
                    // the top of the list is higher up than the snap pos and...
                    // the velocity is directed upwards
                    mSnapping = true;
                }
            }
        }

        private float getDeltaTAndSaveCurrentTime() {
            long now = AnimationUtils.currentAnimationTimeMillis();
            long deltaT = now - mLastTime;
            if (deltaT > MAX_FRAME_DELAY) {
                deltaT = MAX_FRAME_DELAY;
            }
            mLastTime = now;
            return deltaT / 1000f;
        }

        private void scheduleNewFrame() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                postOnAnimationDelayed(this, WANTED_FRAME_DELAY);
            } else {
                postDelayed(this, WANTED_FRAME_DELAY);
            }
        }
    }
}