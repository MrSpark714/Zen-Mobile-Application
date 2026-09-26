package com.example.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.MainActivity;
import com.example.R;

public class TasksFragment extends Fragment {

    private RecyclerView rvTasks;
    private View layoutEmptyTasks;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.page_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvTasks = view.findViewById(R.id.rv_tasks);
        layoutEmptyTasks = view.findViewById(R.id.layout_empty_tasks);

        if (rvTasks != null) {
            rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        }

        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.onTasksFragmentAttached(this);
        }
    }

    public RecyclerView getRecyclerView() {
        return rvTasks;
    }

    public View getEmptyView() {
        return layoutEmptyTasks;
    }

    public void updateListVisibility(boolean isEmpty) {
        if (layoutEmptyTasks != null && rvTasks != null) {
            layoutEmptyTasks.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }
}
