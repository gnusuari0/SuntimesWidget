/**
    Copyright (C) 2020 Forrest Guice
    This file is part of SuntimesWidget.

    SuntimesWidget is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuntimesWidget is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SuntimesWidget.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.forrestguice.suntimeswidget.alarmclock.ui;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.Spannable;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import com.forrestguice.suntimeswidget.AlarmDialog;
import com.forrestguice.suntimeswidget.R;
import com.forrestguice.suntimeswidget.SuntimesUtils;
import com.forrestguice.suntimeswidget.alarmclock.AlarmClockItem;
import com.forrestguice.suntimeswidget.alarmclock.AlarmDatabaseAdapter;
import com.forrestguice.suntimeswidget.alarmclock.AlarmNotifications;
import com.forrestguice.suntimeswidget.alarmclock.AlarmSettings;
import com.forrestguice.suntimeswidget.alarmclock.AlarmState;
import com.forrestguice.suntimeswidget.calculator.core.Location;
import com.forrestguice.suntimeswidget.settings.AppSettings;
import com.forrestguice.suntimeswidget.settings.SolarEvents;
import com.forrestguice.suntimeswidget.settings.WidgetSettings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@SuppressWarnings("Convert2Diamond")
public class AlarmListDialog extends DialogFragment implements LoaderManager.LoaderCallbacks<List<AlarmClockItem>>
{
    public static final String EXTRA_SELECTED_ROWID = "selectedRowID";

    protected View emptyView;
    protected RecyclerView list;
    protected AlarmListDialogAdapter adapter;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedState)
    {
        ContextThemeWrapper contextWrapper = new ContextThemeWrapper(getActivity(), AppSettings.loadTheme(getContext()));
        View content = inflater.cloneInContext(contextWrapper).inflate(R.layout.layout_dialog_alarmlist, parent, false);

        emptyView = content.findViewById(android.R.id.empty);
        emptyView.setOnClickListener(onEmptyViewClick);

        list = (RecyclerView) content.findViewById(R.id.recyclerview);
        list.setLayoutManager(new LinearLayoutManager(getActivity()));
        list.setAdapter(adapter = new AlarmListDialogAdapter(getActivity()));

        adapter.setAdapterListener(adapterListener);

        if (savedState != null) {
            loadSettings(savedState);
        }
        return content;
    }

    @Override
    public void onSaveInstanceState( Bundle outState )
    {
        saveSettings(outState);
        super.onSaveInstanceState(outState);
    }

    protected void loadSettings(Bundle bundle)
    {
        if (adapter != null) {
            adapter.setSelectedRowID(bundle.getLong(EXTRA_SELECTED_ROWID, -1));
        }
    }

    protected void saveSettings(Bundle bundle)
    {
        if (adapter != null) {
            bundle.putLong(EXTRA_SELECTED_ROWID, adapter.getSelectedRowID());
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    protected View.OnClickListener onEmptyViewClick = null;
    protected void setOnEmptyViewClick( View.OnClickListener listener )
    {
        onEmptyViewClick = listener;
        if (emptyView != null) {
            emptyView.setOnClickListener(onEmptyViewClick);
        }
    }

    public long getSelectedRowID() {
        return ((adapter != null) ? adapter.getSelectedRowID() : -1);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void reloadAdapter() {
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public Loader<List<AlarmClockItem>> onCreateLoader(int id, Bundle args) {
        Log.d("DEBUG", "onLoaderCreate");
        return new AlarmListDialogLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<AlarmClockItem>> loader, List<AlarmClockItem> data) {
        Log.d("DEBUG", "onLoadFinished: " + data.size());
        adapter.setItems(data);
        emptyView.setVisibility(data.size() > 0 ? View.GONE : View.VISIBLE);
        list.setVisibility(data.size() > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<List<AlarmClockItem>> loader) {
        Log.d("DEBUG", "onLoadReset");
        adapter.clearItems();
        emptyView.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
    }

    /**
     * Loader
     */
    public static class AlarmListDialogLoader extends AsyncTaskLoader<List<AlarmClockItem>>
    {
        public AlarmListDialogLoader(Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public List<AlarmClockItem> loadInBackground()
        {
            ArrayList<AlarmClockItem> items = new ArrayList<>();
            AlarmDatabaseAdapter db = new AlarmDatabaseAdapter(getContext().getApplicationContext());
            db.open();
            Cursor cursor = db.getAllAlarms(0, true);
            while (!cursor.isAfterLast())
            {
                ContentValues entryValues = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, entryValues);

                AlarmClockItem item = new AlarmClockItem(getContext(), entryValues);
                if (!item.enabled) {
                    AlarmNotifications.updateAlarmTime(getContext(), item);
                }
                items.add(item);
                cursor.moveToNext();
            }
            db.close();
            return items;
        }
    }

    public AlarmClockItem createAlarm(final Context context, AlarmClockItem.AlarmType type, String label, SolarEvents event, Location location, int hour, int minute, String timezone, boolean vibrate, Uri ringtoneUri, ArrayList<Integer> repetitionDays, boolean addToDatabase)
    {
        final AlarmClockItem alarm = new AlarmClockItem();
        alarm.enabled = AlarmSettings.loadPrefAlarmAutoEnable(context);
        alarm.type = type;
        alarm.label = label;
        alarm.hour = hour;
        alarm.minute = minute;
        alarm.timezone = timezone;
        alarm.event = event;
        alarm.location = (location != null ? location : WidgetSettings.loadLocationPref(context, 0));
        alarm.repeating = false;
        alarm.vibrate = vibrate;

        alarm.ringtoneURI = (ringtoneUri != null ? ringtoneUri.toString() : null);
        if (alarm.ringtoneURI != null)
        {
            Ringtone ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
            alarm.ringtoneName = ringtone.getTitle(context);
            ringtone.stop();
        }

        alarm.setState(alarm.enabled ? AlarmState.STATE_NONE : AlarmState.STATE_DISABLED);
        alarm.modified = true;

        if (addToDatabase)
        {
            AlarmDatabaseAdapter.AlarmUpdateTask task = new AlarmDatabaseAdapter.AlarmUpdateTask(context, true, true);
            task.setTaskListener(new AlarmDatabaseAdapter.AlarmItemTaskListener()
            {
                @Override
                public void onFinished(Boolean result, AlarmClockItem item)
                {
                    if (result)
                    {
                        reloadAdapter();

                        if (item.enabled) {
                            context.sendBroadcast( AlarmNotifications.getAlarmIntent(context, AlarmNotifications.ACTION_SCHEDULE, item.getUri()) );
                        }
                    }
                }
            });
            task.execute(alarm);
        }
        return alarm;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * RecyclerView.Adapter
     */
    public static class AlarmListDialogAdapter extends RecyclerView.Adapter<AlarmListDialogItem>
    {
        protected long selectedRowID = -1;
        protected ArrayList<AlarmClockItem> items = new ArrayList<>();
        protected WeakReference<Context> contextRef;

        public AlarmListDialogAdapter(Context context) {
            super();
            contextRef = new WeakReference<>(context);
        }

        public AlarmListDialogAdapter(Context context, List<AlarmClockItem> values)
        {
            super();
            contextRef = new WeakReference<>(context);
            items.addAll(values);
        }

        public void setSelectedRowID(long rowID) {
            selectedRowID = rowID;
            notifyDataSetChanged();
        }

        public void setSelectedIndex(int index) {
            AlarmClockItem item = items.get(index);
            setSelectedRowID(item.rowID);
        }

        public long getSelectedRowID() {
            return selectedRowID;
        }

        public void clearSelection() {
            selectedRowID = -1;
        }

        public void setItems(List<AlarmClockItem> values)
        {
            items.clear();
            items.addAll(values);
            notifyDataSetChanged();
        }

        public void clearItems() {
            items.clear();
            notifyDataSetChanged();
        }

        @Override
        public AlarmListDialogItem onCreateViewHolder(ViewGroup parent, int viewType)
        {
            LayoutInflater layout = LayoutInflater.from(parent.getContext());
            View view = layout.inflate(R.layout.layout_listitem_alarmclock, parent, false);
            return new AlarmListDialogItem(view);
        }

        @Override
        public void onBindViewHolder(AlarmListDialogItem holder, int position)
        {
            AlarmClockItem item = items.get(position);
            holder.isSelected = (item.rowID == selectedRowID);
            holder.bindData(contextRef.get(), items.get(position));
            attachClickListeners(holder, position);
        }

        @Override
        public void onViewRecycled(AlarmListDialogItem holder)
        {
            detachClickListeners(holder);
            holder.isSelected = false;
        }

        private void attachClickListeners(@NonNull final AlarmListDialogItem holder, final int position) {
            holder.card.setOnClickListener(itemClickListener(position));
            holder.card.setOnLongClickListener(itemLongClickListener(position));
            holder.switch_enabled.setOnCheckedChangeListener(alarmEnabledListener(position));
            holder.overflow.setOnClickListener(overflowMenuListener(position));
            holder.typeButton.setOnClickListener(typeMenuListener(position));
        }

        private View.OnClickListener itemClickListener(final int position)
        {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onItemClicked(items.get(position));
                    }
                    setSelectedIndex(position);
                }
            };
        }

        private View.OnLongClickListener itemLongClickListener(final int position)
        {
            return new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v)
                {
                    setSelectedIndex(position);
                    if (listener != null) {
                        return listener.onItemLongClicked(items.get(position));
                    } else return true;
                }
            };
        }

        private View.OnClickListener overflowMenuListener(final int position)
        {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setSelectedIndex(position);
                    if (listener != null) {
                        listener.onOverflowMenu(items.get(position), v);
                    }
                }
            };
        }

        private View.OnClickListener typeMenuListener(final int position)
        {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setSelectedIndex(position);
                    if (listener != null) {
                        listener.onTypeMenu(items.get(position), v);
                    }
                }
            };
        }

        private CompoundButton.OnCheckedChangeListener alarmEnabledListener(final int position)
        {
            return new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (listener != null) {
                        listener.onAlarmToggled(items.get(position), isChecked);
                    }
                }
            };
        }

        private void detachClickListeners(@NonNull AlarmListDialogItem holder) {
            holder.card.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        protected AdapterListener listener;
        public void setAdapterListener( AdapterListener listener ) {
            this.listener = listener;
        }
    }

    /**
     * RecyclerView.ViewHolder
     */
    public static class AlarmListDialogItem extends RecyclerView.ViewHolder
    {
        public static SuntimesUtils utils = new SuntimesUtils();

        public boolean isSelected = false;

        public View card;
        public View cardBackdrop;
        public ImageButton typeButton;
        public TextView text_label;
        public TextView text_event;
        public TextView text_date;
        public TextView text_datetime;
        public TextView text_location;
        public TextView text_ringtone;
        public TextView text_action0;
        public TextView text_action1;
        public CheckBox check_vibrate;
        public TextView text_repeat;
        public TextView text_offset;
        public ImageButton overflow;
        public SwitchCompat switch_enabled;
        public CheckBox check_enabled;

        public AlarmListDialogItem(View view)
        {
            super(view);

            card = view.findViewById(R.id.layout_alarmcard);
            cardBackdrop = view.findViewById(R.id.layout_alarmcard0);
            typeButton = (ImageButton) view.findViewById(R.id.type_menu);
            text_label = (TextView) view.findViewById(android.R.id.text1);
            text_event = (TextView) view.findViewById(R.id.text_event);
            text_date = (TextView) view.findViewById(R.id.text_date);
            text_datetime = (TextView) view.findViewById(R.id.text_datetime);
            text_location = (TextView) view.findViewById(R.id.text_location);
            text_ringtone = (TextView) view.findViewById(R.id.text_ringtone);
            text_action0 = (TextView) view.findViewById(R.id.text_action0);
            text_action1 = (TextView) view.findViewById(R.id.text_action1);
            check_vibrate = (CheckBox) view.findViewById(R.id.check_vibrate);
            text_repeat = (TextView) view.findViewById(R.id.text_repeat);
            text_offset = (TextView) view.findViewById(R.id.text_datetime_offset);
            overflow = (ImageButton) view.findViewById(R.id.overflow_menu);

            if (Build.VERSION.SDK_INT >= 14) {
                switch_enabled = (SwitchCompat) view.findViewById(R.id.switch_enabled);        // switch used by api >= 14 (otherwise null)
            } else {
                check_enabled = (CheckBox) view.findViewById(R.id.switch_enabled);              // checkbox used by api < 14 (otherwise null)
            }
        }

        public void bindData(Context context, @NonNull AlarmClockItem item)
        {
            updateView(context, this, item);
        }

        private void updateView(Context context, AlarmListDialogItem view, @NonNull final AlarmClockItem item)
        {
            int eventType = item.event == null ? -1 : item.event.getType();

            view.cardBackdrop.setBackgroundColor( isSelected ? ColorUtils.setAlphaComponent(Color.CYAN, 170) : Color.TRANSPARENT );  // 66% alpha

            // enabled / disabled
            if (view.switch_enabled != null)
            {
                if (Build.VERSION.SDK_INT >= 14) {
                    view.switch_enabled.setChecked(item.enabled);
                } else {
                    view.check_enabled.setChecked(item.enabled);
                }
            }

            // type button
            if (view.typeButton != null) {
                //view.typeButton.setImageDrawable(ContextCompat.getDrawable(context, (item.type == AlarmClockItem.AlarmType.ALARM ? iconAlarm : iconNotification)));
                view.typeButton.setContentDescription(item.type.getDisplayString());
            }

            // label
            if (view.text_label != null) {
                view.text_label.setText(AlarmItemViewHolder.displayAlarmLabel(context, item));
            }

            // event
            if (view.text_event != null) {
                view.text_event.setText(AlarmItemViewHolder.displayEvent(context, item));
            }

            // time
            if (view.text_datetime != null) {
                view.text_datetime.setText(AlarmItemViewHolder.displayAlarmTime(context, item));
            }

            // date
            if (view.text_date != null) {
                view.text_date.setText(AlarmItemViewHolder.displayAlarmDate(context, item));
                view.text_date.setVisibility((eventType == SolarEvents.TYPE_MOONPHASE || eventType == SolarEvents.TYPE_SEASON) ? View.VISIBLE : View.GONE);
            }

            // location
            if (view.text_location != null) {
                view.text_location.setVisibility((item.event == null && item.timezone == null) ? View.INVISIBLE : View.VISIBLE);
                AlarmDialog.updateLocationLabel(context, view.text_location, item.location);
            }

            // ringtone
            //if (view.text_ringtone != null) {
                //view.text_ringtone.setText( ringtoneDisplayChip(item, isSelected) );
            //}

            // action
            //if (view.text_action0 != null) {
                //view.text_action0.setText( actionDisplayChip(item, 0, isSelected));
                //view.text_action0.setVisibility( item.actionID0 != null ? View.VISIBLE : View.GONE );
            //}

            //if (view.text_action1 != null) {
                //view.text_action1.setText( actionDisplayChip(item, 1, isSelected));
                //view.text_action1.setVisibility( item.actionID1 != null ? View.VISIBLE : View.GONE );
            //}

            // vibrate
            if (view.check_vibrate != null) {
                view.check_vibrate.setChecked(item.vibrate);
                view.check_vibrate.setText( isSelected ? context.getString(R.string.alarmOption_vibrate) : "");
            }

            // repeating
            if (view.text_repeat != null)
            {
                boolean noRepeat = item.repeatingDays == null || item.repeatingDays.isEmpty();
                String repeatText = AlarmClockItem.repeatsEveryDay(item.repeatingDays)
                        ? context.getString(R.string.alarmOption_repeat_all)
                        : noRepeat
                        ? context.getString(R.string.alarmOption_repeat_none)
                        : AlarmRepeatDialog.getDisplayString(context, item.repeatingDays);

                if (item.repeating && (eventType == SolarEvents.TYPE_MOONPHASE || eventType == SolarEvents.TYPE_SEASON)) {
                    repeatText = context.getString(R.string.alarmOption_repeat);
                }

                view.text_repeat.setText(repeatText);
            }

            // offset (before / after)
            if (view.text_offset != null)
            {
                Calendar alarmTime = Calendar.getInstance();
                alarmTime.setTimeInMillis(item.timestamp);
                int alarmHour = SuntimesUtils.is24() ? alarmTime.get(Calendar.HOUR_OF_DAY) : alarmTime.get(Calendar.HOUR);

                if (item.offset == 0)
                {
                    String offsetDisplay = context.getResources().getQuantityString(R.plurals.offset_at_plural, alarmHour);
                    view.text_offset.setText(offsetDisplay);

                } else {
                    boolean isBefore = (item.offset <= 0);
                    String offsetText = utils.timeDeltaLongDisplayString(0, item.offset).getValue();
                    String offsetDisplay = context.getResources().getQuantityString((isBefore ? R.plurals.offset_before_plural : R.plurals.offset_after_plural), alarmHour, offsetText);
                    Spannable offsetSpan = SuntimesUtils.createBoldSpan(null, offsetDisplay, offsetText);
                    view.text_offset.setText(offsetSpan);
                }
            }

            // overflow menu
            if (view.overflow != null) {
                view.overflow.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            }
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    protected AdapterListener listener;
    protected AdapterListener adapterListener = new AdapterListener()
    {
        @Override
        public void onItemClicked(AlarmClockItem item)
        {
            if (listener != null) {
                listener.onItemClicked(item);
            }
        }

        @Override
        public boolean onItemLongClicked(AlarmClockItem item) {
            if (listener != null) {
                return listener.onItemLongClicked(item);
            } else return false;
        }

        @Override
        public void onAlarmToggled(AlarmClockItem item, boolean enabled) {
            if (listener != null) {
                listener.onAlarmToggled(item, enabled);
            }
        }
        @Override
        public void onTypeMenu(AlarmClockItem item, View v) {
            if (listener != null) {
                listener.onTypeMenu(item, v);
            }
        }
        @Override
        public void onOverflowMenu(AlarmClockItem item, View v) {
            if (listener != null) {
                listener.onOverflowMenu(item, v);
            }
        }
    };

    public void setAdapterListener(AdapterListener listener) {
        this.listener = listener;
    }

    public interface AdapterListener
    {
        void onItemClicked(AlarmClockItem item);
        boolean onItemLongClicked(AlarmClockItem item);
        void onAlarmToggled(AlarmClockItem item, boolean enabled);
        void onTypeMenu(AlarmClockItem item, View v);
        void onOverflowMenu(AlarmClockItem item, View v);
    }

}
