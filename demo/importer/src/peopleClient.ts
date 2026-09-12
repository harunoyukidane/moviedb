import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import type { ImporterConfig } from './config.js';
import type { PeoplePort, PersonUpsert } from './ports.js';

const here = dirname(fileURLToPath(import.meta.url));
// proto lives at demo/importer/proto/people.proto; src compiles to dist/, so
// resolve relative to the repo layout in both ts-node and compiled modes.
const PROTO_PATH = join(here, '..', 'proto', 'people.proto');

interface PeopleServiceClient extends grpc.Client {
  createPerson: grpc.handleUnaryCall<any, any> & ((req: any, cb: (e: grpc.ServiceError | null, r: any) => void) => void);
  searchPeople: (req: any, cb: (e: grpc.ServiceError | null, r: any) => void) => void;
}

/**
 * People gRPC adapter (§12.3 step 5). Upsert is idempotent by tmdb_id: attempt
 * CreatePerson (which carries tmdb_id); on ALREADY_EXISTS, resolve the existing
 * person via SearchPeople by name. Deadlines are applied per call.
 */
export class PeopleGrpcClient implements PeoplePort {
  private readonly client: PeopleServiceClient;
  private readonly deadlineMs: number;

  constructor(config: ImporterConfig) {
    const packageDef = protoLoader.loadSync(PROTO_PATH, {
      keepCase: false,
      longs: String,
      enums: String,
      defaults: true,
      oneofs: true
    });
    const proto = grpc.loadPackageDefinition(packageDef) as any;
    const Ctor = proto.catalogue.people.v1.PeopleService;
    this.client = new Ctor(config.peopleGrpcTarget, grpc.credentials.createInsecure());
    this.deadlineMs = config.requestTimeoutMs;
  }

  private deadline(): grpc.CallOptions {
    return { deadline: new Date(Date.now() + this.deadlineMs) };
  }

  async upsertPerson(person: PersonUpsert): Promise<string> {
    const req = {
      tmdbId: person.tmdbId,
      name: person.name,
      biography: person.biography,
      birthDate: person.birthDate ?? undefined,
      deathDate: person.deathDate ?? undefined,
      placeOfBirth: person.placeOfBirth ?? undefined,
      profilePath: person.profilePath ?? undefined
    };
    try {
      const created = await this.unary<any>('createPerson', req);
      return created.id as string;
    } catch (e) {
      const err = e as grpc.ServiceError;
      if (err.code === grpc.status.ALREADY_EXISTS) {
        // Idempotent path: the person already exists for this tmdb_id. Resolve by name.
        const match = await this.findByName(person.name);
        if (match) return match;
      }
      throw e;
    }
  }

  private async findByName(name: string): Promise<string | null> {
    const res = await this.unary<any>('searchPeople', { query: name, limit: 10, offset: 0 });
    const people: any[] = res.people ?? [];
    const exact = people.find((p) => p.name === name) ?? people[0];
    return exact?.id ?? null;
  }

  private unary<T>(method: 'createPerson' | 'searchPeople', req: unknown): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      (this.client as any)[method](req, this.deadline(), (err: grpc.ServiceError | null, res: T) => {
        if (err) reject(err);
        else resolve(res);
      });
    });
  }

  close() {
    this.client.close();
  }
}
