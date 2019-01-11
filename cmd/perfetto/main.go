// Copyright (C) 2019 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"bufio"
	"context"
	"fmt"
	"io/ioutil"
	"os"
	"strings"

	"github.com/google/gapid/gapis/perfetto"
)

func main() {
	if err := run(context.Background()); err != nil {
		fmt.Println("Error:", err)
	}
}

func run(ctx context.Context) error {
	data, err := ioutil.ReadFile("/usr/local/google/work/gapid/captures/com.imangi.templerun2_20190109_0749.perfetto")
	if err != nil {
		return err
	}

	fmt.Println("Loading data...")
	p, err := perfetto.NewProcessor(ctx, data)
	if err != nil {
		return err
	}
	defer p.Close()

	fmt.Println("Ready.")

	reader := bufio.NewReader(os.Stdin)
	for {
		fmt.Print("Q: ")
		q, err := reader.ReadString('\n')
		if err != nil {
			return err
		}
		q = strings.TrimSpace(q)
		if strings.ToLower(q) == "quit" {
			return nil
		}

		fmt.Println("Executing...")
		r := p.Query(q)
		if !r.Ready.Wait(ctx) {
			return nil
		}
		print(r)
	}
}

func print(r *perfetto.Result) {
	if e := r.Result.Error; e != "" {
		fmt.Println("Query failed:", e)
	}

	for _, c := range r.Result.ColumnDescriptors {
		fmt.Printf("%s\t", c.Name)
	}
	fmt.Println()
	fmt.Println("===========================================================")

	for row := uint64(0); row < r.Result.NumRecords; row++ {
		for _, c := range r.Result.Columns {
			if c.IsNulls[row] {
				fmt.Print("NULL\t")
			} else {
				switch {
				case c.LongValues != nil:
					fmt.Printf("%v\t", c.LongValues[row])
				case c.DoubleValues != nil:
					fmt.Printf("%v\t", c.DoubleValues[row])
				case c.StringValues != nil:
					fmt.Printf("%v\t", c.StringValues[row])
				}
			}
		}
		fmt.Println()
	}
}
