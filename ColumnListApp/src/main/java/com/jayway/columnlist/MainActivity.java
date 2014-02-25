package com.jayway.columnlist;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends ActionBarActivity {

    private boolean mSelectMode;
    private MyAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdapter = new MyAdapter(this, 50);
        ColumnListView listView = (ColumnListView) findViewById(R.id.list);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                if (mSelectMode) {
                    mAdapter.toggleSelected(position);
                } else {
                    Toast.makeText(MainActivity.this, "Clicked item: " + position, Toast.LENGTH_SHORT).show();
                }
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mSelectMode = true;
                invalidateOptionsMenu();
                mAdapter.toggleSelected(position);
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        menu.findItem(R.id.action_ok).setVisible(mSelectMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_ok) {
            mSelectMode = false;
            invalidateOptionsMenu();

            Toast.makeText(this, "Selected items: " + getSelectedPositionsText(), Toast.LENGTH_SHORT).show();

            mAdapter.clearSelectedPositions();
            return true;
        }
        return false;
    }

    private String getSelectedPositionsText() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < mAdapter.mSelectedPositions.size(); i++) {
            if (mAdapter.mSelectedPositions.get(i)) {
                sb.append(" ");
                sb.append(i);
            }
        }

        return sb.toString();
    }

    private class MyAdapter extends BaseAdapter {
        Context mContext;
        int mCount;
        ArrayList<Integer> mHeights = new ArrayList<Integer>();
        ArrayList<Integer> mColors = new ArrayList<Integer>();
        ArrayList<Boolean> mSelectedPositions = new ArrayList<Boolean>();

        public MyAdapter(final Context context, int count) {
            mContext = context;
            mCount = count;
            for (int i = 0; i < mCount; i++) {
                mHeights.add((int) (Math.random() * 800 + 200));
                mColors.add(Color.rgb((int) (Math.random() * 0x80), (int) (Math.random() * 0x80), (int) (Math.random() * 0x80)));
                mSelectedPositions.add(false);
            }
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public Object getItem(final int position) {
            return position;
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            TextView view = (TextView) convertView;
            if (view == null) {
                view = (TextView) LayoutInflater.from(mContext).inflate(R.layout.list_item, null, false);
            }
            view.setText("View " + position);
            AbsListView.LayoutParams params = (AbsListView.LayoutParams) view.getLayoutParams();

            if (params == null) {
                params = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, mHeights.get(position));
            } else {
                params.height = mHeights.get(position);
            }
            view.setLayoutParams(params);

            view.setBackgroundColor(mSelectedPositions.get(position) ? 0xFF000000 : mColors.get(position));

            return view;
        }

        private void toggleSelected(final int position) {
            mSelectedPositions.set(position, !mSelectedPositions.get(position));
            notifyDataSetChanged();
        }

        public void clearSelectedPositions() {
            for (int i = 0; i < mSelectedPositions.size(); i++) {
                mSelectedPositions.set(i, false);
            }
            notifyDataSetChanged();
        }
    }
}
