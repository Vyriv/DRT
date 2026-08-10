package dev.vy.drt.tracking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class DiagnosticIncident {
	private final String id;
	private final String incidentType;
	private final DiagnosticSeverity severity;
	private final String rootKey;
	private final String likelyCause;
	private final String investigationLocation;
	private final Instant createdAt;
	private final long firstSequence;
	private final EnumSet<TrackerInvariant> invariants = EnumSet.noneOf(TrackerInvariant.class);
	private final List<DiagnosticEntry> entries = new ArrayList<>();
	private int updateCount;
	private boolean userNotified;
	private String replayPath = "";

	DiagnosticIncident(
		String id,
		String incidentType,
		DiagnosticSeverity severity,
		String rootKey,
		String likelyCause,
		String investigationLocation,
		Instant createdAt,
		long firstSequence,
		TrackerInvariant invariant,
		DiagnosticEntry firstEntry
	) {
		this.id = id;
		this.incidentType = incidentType == null || incidentType.isBlank() ? "TRACKING_INVARIANT" : incidentType;
		this.severity = severity == null ? DiagnosticSeverity.ERROR : severity;
		this.rootKey = rootKey == null ? "" : rootKey;
		this.likelyCause = likelyCause == null ? "" : likelyCause;
		this.investigationLocation = investigationLocation == null ? "" : investigationLocation;
		this.createdAt = createdAt == null ? Instant.EPOCH : createdAt;
		this.firstSequence = firstSequence;
		if (invariant != null) invariants.add(invariant);
		if (firstEntry != null) entries.add(firstEntry);
	}

	public String id() {
		return id;
	}

	public String incidentType() {
		return incidentType;
	}

	public DiagnosticSeverity severity() {
		return severity;
	}

	public String rootKey() {
		return rootKey;
	}

	public String likelyCause() {
		return likelyCause;
	}

	public String investigationLocation() {
		return investigationLocation;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public long firstSequence() {
		return firstSequence;
	}

	public Set<TrackerInvariant> invariants() {
		return Set.copyOf(invariants);
	}

	public List<DiagnosticEntry> entries() {
		return List.copyOf(entries);
	}

	public int updateCount() {
		return updateCount;
	}

	public boolean userNotified() {
		return userNotified;
	}

	public void markUserNotified() {
		userNotified = true;
	}

	public String replayPath() {
		return replayPath;
	}

	public void setReplayPath(String replayPath) {
		this.replayPath = replayPath == null ? "" : replayPath;
	}

	void append(TrackerInvariant invariant, DiagnosticEntry entry) {
		if (invariant != null) invariants.add(invariant);
		if (entry != null) entries.add(entry);
		updateCount++;
	}
}
