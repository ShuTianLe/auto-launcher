import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { LoginForm } from "@/components/LoginForm";
import { AUTH_COOKIE, isValidSessionToken } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function LoginPage() {
  const token = (await cookies()).get(AUTH_COOKIE)?.value;
  if (isValidSessionToken(token)) {
    redirect("/");
  }

  return <LoginForm />;
}
