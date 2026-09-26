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

public class NotesFragment extends Fragment {

    private RecyclerView rvNotes;
    private View layoutEmptyNotes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.page_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvNotes = view.findViewById(R.id.rv_notes);
        layoutEmptyNotes = view.findViewById(R.id.layout_empty_notes);

        if (rvNotes != null) {
            rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        }

        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.onNotesFragmentAttached(this);
        }
    }

    public RecyclerView getRecyclerView() {
        return rvNotes;
    }

    public View getEmptyView() {
        return layoutEmptyNotes;
    }

    public void updateListVisibility(boolean isEmpty) {
        if (layoutEmptyNotes != null && rvNotes != null) {
            layoutEmptyNotes.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rvNotes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }
}
