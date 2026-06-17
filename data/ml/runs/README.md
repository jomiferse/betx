# BetX ML Run Artifacts

This directory is ignored by default because ML runs can contain trained models, full predictions, and other large local artifacts.

Only small decision artifacts for the paused ML line should be committed. The retained decision run is `ml-002-1-ablation-check`, limited to:

- `ablation_summary.json`
- `ablation_results.csv`
- `model_comparison.csv`
- `dataset_quality.json`
- `dataset_split_metadata.json`

Do not commit `model.joblib`, full `predictions.csv`, `bets.csv`, or complete run directories.
