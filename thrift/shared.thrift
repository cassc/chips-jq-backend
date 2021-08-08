namespace java chips
namespace php chips

struct Response{
  1: i16 code;
  2: optional string msg;
  3: optional string json;
  4: optional binary file;
}

struct Request{
  1: map<string,string> content;
  2: optional binary file;
}

service UserService {
  string ping();
  Response registerUser(1:map<string,string> m),
  Response updatePassword(1:map<string,string> m),
  Response deleteUser(1:i64 uid),
  Response updateUser(1:map<string,string> m),
  Response vericode(1:map<string,string> m),
  Response userInfo(1:map<string,string> m),
  Response addRole(1:Request req),
  Response updateRole(1:Request req)
}
