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

package capture

import (
	"context"
	"io"
	"io/ioutil"

	"github.com/google/gapid/core/app/analytics"
	"github.com/google/gapid/gapis/perfetto"
	"github.com/google/gapid/gapis/service"
	"github.com/google/gapid/gapis/service/path"
	"github.com/pkg/errors"
)

type PerfettoCapture struct {
	name      string
	Processor *perfetto.Processor
}

func (c *PerfettoCapture) Name() string {
	return c.name
}

func (c *PerfettoCapture) Service(ctx context.Context, p *path.Capture) *service.Capture {
	return &service.Capture{
		Type: service.TraceType_Perfetto,
		Name: c.name,
	}
}

func (c *PerfettoCapture) Export(ctx context.Context, w io.Writer) error {
	return errors.New("export not supported")
}

func deserializePerfettoTrace(ctx context.Context, r *Record, in io.Reader) (Capture, error) {
	stopTiming := analytics.SendTiming("perfetto", "deserialize")
	defer stopTiming(analytics.Size(len(r.Data)))

	data, err := ioutil.ReadAll(in)
	if err != nil {
		return nil, err
	}

	p, err := perfetto.NewProcessor(ctx, data)
	if err != nil {
		return nil, err
	}
	return &PerfettoCapture{r.Name, p}, nil
}
