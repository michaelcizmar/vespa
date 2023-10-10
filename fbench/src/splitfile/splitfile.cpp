// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <util/filereader.h>
#include <fstream>
#include <vector>
#include <memory>
#include <unistd.h>

/**
 * Split a text file randomly in a number of parts.  Process an input
 * file (or stdin) line by line, writing each line out to a randomly
 * chosen output file. The output files are numbered using a counter
 * and a filename pattern.
 **/

int
main(int argc, char** argv)
{
    // parameters with default values.
    const char *pattern = "query%03d.txt";
    int linebufsize = 10240;

    // parse options and override defaults.
    int         opt;
    bool        optError;

    optError = false;
    while((opt = getopt(argc, argv, "p:m:")) != -1) {
        switch(opt) {
        case 'p':
            pattern = optarg;
            break;
        case 'm':
            linebufsize = atoi(optarg);
            if (linebufsize < 10240) {
                linebufsize = 10240;
            }
            break;
        default:
            optError = true;
            break;
        }
    }

    if (argc < (optind + 1) || argc > (optind + 2) || optError) {
        printf("usage: vespa-fbench-split-file [-p pattern] [-m maxLineSize] <numparts> [<file>]\n\n");
        printf(" -p pattern : output name pattern ['query%%03d.txt']\n");
        printf(" -m <num>   : max line size for input/output lines.\n");
        printf("              Can not be less than the default [10240]\n");
        printf(" <numparts> : number of output files to generate.\n\n");
        printf("Reads from <file> (stdin if <file> is not given) and\n");
        printf("randomly distributes each line between <numpart> output\n");
        printf("files. The names of the output files are generated by\n");
        printf("combining the <pattern> with sequential numbers using\n");
        printf("the sprintf function.\n");
        return -1;
    }

    int outcnt = atoi(argv[optind]);
    if (outcnt < 1) {
        printf("too few output files!\n");
        return -1;
    }

    int i;
    int res;
    std::vector<char> linebuf(linebufsize);
    char filename[1024];
    std::unique_ptr<FileReader> input = std::make_unique<FileReader>();
    std::vector<std::unique_ptr<std::ostream>> output;

    if (argc > (optind + 1)) {
        if (!input->Open(argv[optind + 1])) {
            printf("could not open input file!\n");
            return -1;
        }
    } else {
        if (!input->OpenStdin()) {
            printf("could not open stdin! (strange)\n");
            return -1;
        }
    }

    // open output files
    output.reserve(outcnt);
    for (i = 0; i < outcnt; i++) {
        snprintf(filename, 1024, pattern, i);
        output.emplace_back(std::make_unique<std::ofstream>(filename, std::ofstream::out | std::ofstream::binary | std::ofstream::trunc));
        if (! output.back()) {
            printf("could not open output file: %s\n", filename);
            input->Close();
            return -1;
        }
    }

    // split file
    while ((res = input->ReadLine(&linebuf[0], linebufsize - 1)) >= 0) {
        if (res < linebufsize - 1) {
            linebuf[res] = '\n';
            linebuf[res + 1] = '\0'; // just in case
            i = random() % outcnt;
            if (!output[i]->write(&linebuf[0], res + 1)) {
                printf("error writing to file '%d'\n", i);
            }
        } else {
            printf("line too long, skipping...\n");
        }
    }

    // close files
    input->Close();
    return 0;
}
