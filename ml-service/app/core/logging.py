"""Process-wide logging configuration."""

import logging

_FORMAT = "%(asctime)s %(levelname)s %(name)s - %(message)s"


def configure_logging(level: str) -> None:
    """Configure the root logger once; later calls only update the level."""
    root = logging.getLogger()
    if not root.handlers:
        logging.basicConfig(format=_FORMAT)
    root.setLevel(level)
