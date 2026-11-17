"""Offline training pipeline for AgriGenius ML models (not imported by the web app at request time).

The crop-recommendation artifact may reference ``training.artifact`` classes when unpickled,
so this package must stay importable from the ``ml-service`` directory.
"""
