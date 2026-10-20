/** The user fields the hub displays; structurally compatible with `User` from `@openjcockpit/auth`. */
export interface HubUser {
  username: string;
  firstName: string;
  email: string | undefined;
}
