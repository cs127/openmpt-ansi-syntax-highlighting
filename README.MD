# OpenMPT ANSI Syntax Highlighting

## Info

This Java program adds syntax highlighting to your clipboard (using ANSI color codes) when copying pattern data in [OpenMPT](https://openmpt.org).

The main purpose of this program is to paste syntax-highlighted OpenMPT pattern data in [Discord](https://discord.com), using `ansi` markdown code blocks.

## Command-line usage

`[EXEC] [OPTIONS] [COLORS]`

Options:
* `-h` or `--help`: Help (show all available options)
* `-i`: Read input from STDIN instead of clipboard
* `-o`: Write output to STDOUT instead of clipboard
* `-d`: Automatically wrap output in `ansi` markdown code block (for Discord)

Colors:
`X,X,X,X,X,X,X` - Each value from 0 to 15 (Discord only supports 0 to 7)\
format: `default,note,instrument,volume,panning,pitch,global`\
if not provided: `7,5,4,2,6,3,1`
